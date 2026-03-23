package com.bif.app.data.repository;

import android.content.Context;
import android.location.Address;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.PlaceDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.PlaceMapper;
import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.data.source.local.PlaceDao;
import com.bif.app.data.source.local.SearchHistoryDao;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IPlaceRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

import retrofit2.Response;

public class PlaceRepository implements IPlaceRepository {

    private static final String TAG = "PlaceRepository";
    private static final int MAX_LOCAL_PLACES = 100;

    private final GoogleMapsDataSource googleMapsDataSource;
    private final RestApiService restApiService;
    private final PlaceDao placeDao;
    private final SearchHistoryDao searchHistoryDao;
    private final SyncManager syncManager;
    private final NetworkMonitor networkMonitor;
    private final ExecutorService executorService;
    private final String activeUserId;

    @Inject
    public PlaceRepository(GoogleMapsDataSource googleMapsDataSource,
                           RestApiService restApiService,
                           PlaceDao placeDao,
                           SearchHistoryDao searchHistoryDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor,
                           @ApplicationContext Context appContext) {
        this.googleMapsDataSource = googleMapsDataSource;
        this.restApiService = restApiService;
        this.placeDao = placeDao;
        this.searchHistoryDao = searchHistoryDao;
        this.syncManager = syncManager;
        this.networkMonitor = networkMonitor;
        this.executorService = Executors.newFixedThreadPool(4);
        this.activeUserId = resolveActiveUserId(appContext);

        if (!activeUserId.trim().isEmpty()) {
            syncManager.setUserContext(activeUserId, null);
        }
    }

    // Backward-compatible constructor used by tests.
    public PlaceRepository(GoogleMapsDataSource googleMapsDataSource,
                           RestApiService restApiService,
                           PlaceDao placeDao,
                           SearchHistoryDao searchHistoryDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor) {
        this(googleMapsDataSource, restApiService, placeDao,
                searchHistoryDao, syncManager, networkMonitor, null);
    }

    @Override
    public LiveData<Location> searchLocation(String query) {
        MutableLiveData<Location> result = new MutableLiveData<>();
        executorService.execute(() -> {
            try {
                List<Address> results =
                        googleMapsDataSource.geocodeLocation(query);
                if (results != null && !results.isEmpty()) {
                    Address address = results.get(0);
                    Location location = new Location();
                    location.latitude = address.getLatitude();
                    location.longitude = address.getLongitude();
                    result.postValue(location);
                } else {
                    result.postValue(null);
                }
            } catch (IOException e) {
                result.postValue(null);
            }
        });
        return result;
    }

    @Override
    public LiveData<List<Place>> searchPlaces(String query) {
        return doSearch(query, true);
    }

    @Override
    public LiveData<List<Place>> searchPlacesFromHistory(String query) {
        return doSearch(query, false);
    }

    private LiveData<List<Place>> doSearch(String query, boolean saveToHistory) {
        MutableLiveData<List<Place>> result = new MutableLiveData<>();

        if (query == null || query.isEmpty()) {
            result.postValue(new ArrayList<>());
            return result;
        }

        executorService.execute(() -> {
            List<Place> combinedResults = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            // Step 1: Always search local Room cache first (includes favorites, persisted places)
            String queryLower = query.toLowerCase(java.util.Locale.getDefault());
            List<PlaceEntity> localMatches = placeDao.searchByName(
                    queryLower, activeUserId);
            if (localMatches != null) {
                for (PlaceEntity entity : localMatches) {
                    combinedResults.add(PlaceMapper.toDomain(entity));
                    seenIds.add(entity.id);
                }
            }

            // Step 2: Query server + Google when online, deduplicating against local results
            if (networkMonitor.isOnline()) {
                try {
                    Response<List<PlaceDto>> response = restApiService
                            .searchServerPlaces(query).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        for (PlaceDto dto : response.body()) {
                            if (!seenIds.contains(dto.id)) {
                                combinedResults.add(PlaceMapper.fromDto(dto, true));
                                seenIds.add(dto.id);
                            }
                            // Always keep local cache up-to-date from server
                            placeDao.upsert(PlaceMapper.fromDto(dto,
                                    activeUserId));
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Server search failed", e);
                }

                // Step 3: Google Places API to fill any remaining gaps
                try {
                    List<Address> googleResults =
                            googleMapsDataSource.geocodeLocation(query);
                    if (googleResults != null) {
                        for (Address address : googleResults) {
                            String id = "google_"
                                    + address.getLatitude() + "_"
                                    + address.getLongitude();
                            if (!seenIds.contains(id)) {
                                Place place = new Place(
                                        id,
                                        address.getFeatureName() != null
                                                ? address.getFeatureName()
                                                : query,
                                        address.getAddressLine(0) != null
                                                ? address.getAddressLine(0)
                                                : "",
                                        0.0,
                                        new Location(address.getLatitude(),
                                                address.getLongitude()));
                                combinedResults.add(place);
                                seenIds.add(id);
                                autoPersistFromSearch(place);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Google search failed", e);
                }
            }
            // Offline: local cache results (Step 1) are already included above

            if (saveToHistory) {
                saveSearchHistory(query);
            }

            cacheResultsLocally(combinedResults);
            result.postValue(combinedResults);
        });

        return result;
    }

    @Override
    public void persistPlace(Place place, String action) {
        executorService.execute(() -> {
            PlaceEntity existing = placeDao.getByIdSync(place.id,
                    activeUserId);

            // Cache locally
            PlaceEntity entity = PlaceMapper.toEntity(place,
                    activeUserId);
            entity.persistedByAction = action;
            if (existing != null) {
                entity.serverVersion = existing.serverVersion;
            }

            boolean isDelete = "DELETE".equalsIgnoreCase(action)
                    || "REMOVE".equalsIgnoreCase(action);
            if (isDelete) {
                entity.deleted = true;
            }

            placeDao.upsert(entity);

            // Enforce local cache limit
            enforceLocalCacheLimit();

            // Enqueue for server sync
            String operation;
            if (isDelete) {
                operation = "DELETE";
            } else if (existing == null || existing.deleted) {
                operation = "CREATE";
            } else {
                operation = "UPDATE";
            }

            PlaceDto payload = PlaceMapper.toDto(place, activeUserId);
            payload.placeSource = entity.placeSource;
            payload.persistedByAction = action;
            payload.persistedByUserId = activeUserId;
            payload.serverVersion = entity.serverVersion;
            payload.deleted = entity.deleted;

            syncManager.enqueueChange(
                    "place",
                    place.id,
                    operation,
                    UUID.randomUUID().toString(),
                    payload);
            syncManager.syncIfOnline();
        });
    }

    @Override
    public LiveData<List<Place>> getAllPersistedPlaces() {
        return Transformations.map(placeDao.getAll(activeUserId),
                PlaceMapper::toDomainList);
    }

    @Override
    public LiveData<List<String>> getSearchHistory() {
        return Transformations.map(searchHistoryDao.getRecent(), entities -> {
            List<String> queries = new ArrayList<>();
            if (entities != null) {
                for (com.bif.app.data.source.local.entity.SearchHistoryEntity e : entities) {
                    queries.add(e.query);
                }
            }
            return queries;
        });
    }

    private void saveSearchHistory(String query) {
        SearchHistoryEntity entry = new SearchHistoryEntity();
        entry.query = query;
        entry.searchedAt = System.currentTimeMillis();
        searchHistoryDao.insert(entry);
        searchHistoryDao.evictOldest();
    }

    private void cacheResultsLocally(List<Place> places) {
        List<PlaceEntity> entities = new ArrayList<>();
        for (Place place : places) {
            entities.add(PlaceMapper.toEntity(place, activeUserId));
        }
        if (!entities.isEmpty()) {
            placeDao.upsertAll(entities);
        }
        enforceLocalCacheLimit();
    }

    private void enforceLocalCacheLimit() {
        int count = placeDao.count(activeUserId);
        if (count > MAX_LOCAL_PLACES) {
            placeDao.evictOldest(count - MAX_LOCAL_PLACES, activeUserId);
        }
    }

    private void autoPersistFromSearch(Place place) {
        executorService.execute(() -> {
            if (!networkMonitor.isOnline()) {
                return;
            }
            try {
                PlaceDto dto = PlaceMapper.toDto(place, activeUserId);
                dto.placeSource = "google_maps";
                dto.persistedByAction = "search_discovered";
                dto.persistedByUserId = activeUserId;
                Response<PlaceDto> response = restApiService
                        .saveFromSearch(dto).execute();
                if (response.isSuccessful() && response.body() != null) {
                    placeDao.upsert(PlaceMapper.fromDto(response.body(),
                            activeUserId));
                }
            } catch (IOException e) {
                Log.e(TAG, "Auto-persist from search failed", e);
            }
        });
    }

    private String resolveActiveUserId(Context appContext) {
        if (appContext == null) {
            return "anonymous";
        }
        String username = UserPreferences.getUsername(appContext);
        if (username.trim().isEmpty()) {
            return "anonymous";
        }
        return username.trim();
    }
}
