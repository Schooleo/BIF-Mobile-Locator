package com.bif.app.data.repository;

import android.content.Context;
import android.location.Address;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.AiGraphQlClient;
import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.ai.AiPlaceSuggestionPayload;
import com.bif.app.core.network.dto.ai.AiSuggestedPlacePayload;
import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.core.network.dto.place.PlaceSearchRequestDTO;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.PlaceMapper;
import com.bif.app.data.source.AndroidGeocodingDataSource;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.SearchHistoryDao;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.AiPlaceSuggestion;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
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
    private static final int MAX_LOCAL_PLACES = 500;
    private static final String ADDRESS_UNAVAILABLE = "Address unavailable";

    private final AndroidGeocodingDataSource geocodingDataSource;
    private final RestApiService restApiService;
    private final PlaceDao placeDao;
    private final SearchHistoryDao searchHistoryDao;
    private final SyncManager syncManager;
    private final NetworkMonitor networkMonitor;
    private final AiGraphQlClient aiGraphQlClient;
    private final ExecutorService executorService;
    private final String activeUserId;
    private volatile Double configuredDefaultLatitude;
    private volatile Double configuredDefaultLongitude;

    @Inject
    public PlaceRepository(AndroidGeocodingDataSource geocodingDataSource,
                           RestApiService restApiService,
                           PlaceDao placeDao,
                           SearchHistoryDao searchHistoryDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor,
                           AiGraphQlClient aiGraphQlClient,
                           @ApplicationContext Context appContext) {
        this.geocodingDataSource = geocodingDataSource;
        this.restApiService = restApiService;
        this.placeDao = placeDao;
        this.searchHistoryDao = searchHistoryDao;
        this.syncManager = syncManager;
        this.networkMonitor = networkMonitor;
        this.aiGraphQlClient = aiGraphQlClient;
        this.executorService = Executors.newFixedThreadPool(4);
        this.activeUserId = resolveActiveUserId(appContext);
        this.configuredDefaultLatitude = null;
        this.configuredDefaultLongitude = null;

        if (!activeUserId.trim().isEmpty()) {
            syncManager.setUserContext(activeUserId, null);
        }
    }

    public PlaceRepository(AndroidGeocodingDataSource geocodingDataSource,
                           RestApiService restApiService,
                           PlaceDao placeDao,
                           SearchHistoryDao searchHistoryDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor,
                           AiGraphQlClient aiGraphQlClient,
                           @ApplicationContext Context appContext,
                           Double defaultLatitude,
                           Double defaultLongitude) {
        this(geocodingDataSource, restApiService, placeDao,
                searchHistoryDao, syncManager, networkMonitor,
                aiGraphQlClient, appContext);
        setDefaultSearchCoordinates(defaultLatitude, defaultLongitude);
    }

    public PlaceRepository(AndroidGeocodingDataSource geocodingDataSource,
                           RestApiService restApiService,
                           PlaceDao placeDao,
                           SearchHistoryDao searchHistoryDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor) {
        this(geocodingDataSource, restApiService, placeDao,
                searchHistoryDao, syncManager, networkMonitor, null, null);
    }

    // Package-private constructor for testing with custom executor
    PlaceRepository(AndroidGeocodingDataSource geocodingDataSource,
                    RestApiService restApiService,
                    PlaceDao placeDao,
                    SearchHistoryDao searchHistoryDao,
                    SyncManager syncManager,
                    NetworkMonitor networkMonitor,
                    AiGraphQlClient aiGraphQlClient,
                    String activeUserId,
                    ExecutorService executorService) {
        this.geocodingDataSource = geocodingDataSource;
        this.restApiService = restApiService;
        this.placeDao = placeDao;
        this.searchHistoryDao = searchHistoryDao;
        this.syncManager = syncManager;
        this.networkMonitor = networkMonitor;
        this.aiGraphQlClient = aiGraphQlClient;
        this.executorService = executorService;
        this.activeUserId = activeUserId;
        this.configuredDefaultLatitude = null;
        this.configuredDefaultLongitude = null;
    }

    public void setDefaultSearchCoordinates(Double latitude, Double longitude) {
        this.configuredDefaultLatitude = latitude;
        this.configuredDefaultLongitude = longitude;
    }

    @Override
    public LiveData<Location> searchLocation(String query) {
        MutableLiveData<Location> result = new MutableLiveData<>();
        executorService.execute(() -> {
            try {
                List<Address> results =
                    geocodingDataSource.geocodeLocation(query);
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
    public LiveData<List<Place>> searchPlaces(String query, Location userLocation) {
        return doSearch(query, true, userLocation);
    }

    @Override
    public LiveData<List<Place>> searchPlacesFromHistory(String query) {
        return doSearch(query, false, null);
    }

    @Override
    public LiveData<AiPlaceSuggestionResult> suggestPlacesFromQuery(String query) {
        MutableLiveData<AiPlaceSuggestionResult> result = new MutableLiveData<>();

        if (query == null || query.trim().isEmpty()) {
            result.setValue(new AiPlaceSuggestionResult(new ArrayList<>(),
                    new ArrayList<>(), null));
            return result;
        }

        if (!networkMonitor.isOnline()) {
            result.setValue(new AiPlaceSuggestionResult(new ArrayList<>(),
                    new ArrayList<>(), "OFFLINE"));
            return result;
        }

        if (aiGraphQlClient == null) {
            result.postValue(new AiPlaceSuggestionResult(new ArrayList<>(),
                    new ArrayList<>(), "AI_FAILURE"));
            return result;
        }

        aiGraphQlClient.suggestPlacesFromQuery(query)
                .whenComplete((payload, throwable) -> {
            if (throwable != null || payload == null) {
                Log.e(TAG, "AI suggest query failed", throwable);
                result.postValue(new AiPlaceSuggestionResult(new ArrayList<>(),
                        new ArrayList<>(), "AI_FAILURE"));
                return;
            }

                List<String> warnings = payload.warnings != null
                    ? new ArrayList<>(payload.warnings)
                    : new ArrayList<>();

                String failureCode = payload.failureCode;

                if (failureCode != null) {
                    result.postValue(new AiPlaceSuggestionResult(new ArrayList<>(),
                            warnings, failureCode));
                    return;
                }

                List<AiPlaceSuggestion> mappedPlaces = new ArrayList<>();
                if (payload.places != null) {
                    for (AiSuggestedPlacePayload placeNode : payload.places) {
                        if (placeNode == null) {
                            continue;
                        }

                        if (!hasText(placeNode.id)
                                || !hasText(placeNode.name)
                                || !hasText(placeNode.address)
                                || placeNode.latitude == null
                                || placeNode.longitude == null
                                || !isValidCoordinate(placeNode.latitude, placeNode.longitude)) {
                            continue;
                        }

                    double rating = placeNode.rating;
                    int addedToTripCount = placeNode.addedToTripCount;

                        Place place = new Place(
                                placeNode.id,
                                placeNode.name,
                                placeNode.address,
                                rating,
                                new Location(placeNode.latitude, placeNode.longitude)
                        );
                        mappedPlaces.add(new AiPlaceSuggestion(place, addedToTripCount));
                    }
                }

                result.postValue(new AiPlaceSuggestionResult(mappedPlaces,
                        warnings, null));
        });

        return result;
    }

    private LiveData<List<Place>> doSearch(String query, boolean saveToHistory,
                                           Location userLocation) {
        MutableLiveData<List<Place>> result = new MutableLiveData<>();

        if (query == null || query.isEmpty()) {
            result.postValue(new ArrayList<>());
            return result;
        }

        executorService.execute(() -> {
            List<Place> combinedResults = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            Set<String> seenKeys = new HashSet<>();

            if (networkMonitor.isOnline()) {
                try {
                    Location validLocation = null;
                    if (userLocation != null 
                            && userLocation.latitude >= -90 && userLocation.latitude <= 90
                            && userLocation.longitude >= -180 && userLocation.longitude <= 180) {
                        validLocation = userLocation;
                    }
                    Double lat = validLocation != null ? validLocation.latitude : null;
                    Double lng = validLocation != null ? validLocation.longitude : null;
                    boolean hasInvalidCoordinates = lat == null
                            || lng == null
                            || (Double.compare(lat, 0.0d) == 0 && Double.compare(lng, 0.0d) == 0);
                    if (hasInvalidCoordinates) {
                        Location fallbackLocation = resolveDefaultSearchLocation();
                        if (fallbackLocation == null) {
                            // No valid default location configured, skip remote search
                            Log.d(TAG, "No valid default search location configured");
                        } else {
                            lat = fallbackLocation.latitude;
                            lng = fallbackLocation.longitude;
                        }
                    }
                    if (lat != null && lng != null) {
                        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
                        request.query = query;
                        request.latitude = lat;
                        request.longitude = lng;
                        Log.d(TAG, "Search request coordinates: coordinates present");
                        Response<List<PlaceDto>> response = restApiService
                            .searchServerPlaces(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            for (PlaceDto dto : response.body()) {
                                if (!isValidCoordinate(dto.latitude, dto.longitude)) {
                                    continue;
                                }

                                Place mappedPlace = PlaceMapper.fromDto(dto, true);
                                String dedupKey = buildDedupKey(mappedPlace);
                                boolean isNewId = !seenIds.contains(dto.id);
                                boolean isNewKey = !seenKeys.contains(dedupKey);

                                if (isNewId && isNewKey) {
                                    combinedResults.add(mappedPlace);
                                    seenIds.add(dto.id);
                                    seenKeys.add(dedupKey);
                                }
                                placeDao.upsert(PlaceMapper.fromDto(dto,
                                        activeUserId));
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Server search failed", e);
                }

                try {
                    List<Address> geocoderResults =
                            geocodingDataSource.geocodeLocation(query);
                    if (geocoderResults != null) {
                        for (Address address : geocoderResults) {
                            if (!isValidCoordinate(address.getLatitude(),
                                    address.getLongitude())) {
                                continue;
                            }

                            String id = "geocode_"
                                    + address.getLatitude() + "_"
                                    + address.getLongitude();
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
                            String dedupKey = buildDedupKey(place);

                            if (!seenIds.contains(id)
                                    && !seenKeys.contains(dedupKey)) {
                                combinedResults.add(place);
                                seenIds.add(id);
                                seenKeys.add(dedupKey);
                                // TODO: auto-persist remains disabled to avoid caching transient geocoder hits.
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Geocoder search failed", e);
                }
            }

            String queryLower = query.toLowerCase(java.util.Locale.getDefault());
            List<PlaceEntity> localMatches = placeDao.searchByName(
                    queryLower, activeUserId);
            if (localMatches != null) {
                for (PlaceEntity entity : localMatches) {
                    if (!isValidCoordinate(entity.latitude, entity.longitude)) {
                        continue;
                    }

                    Place mappedPlace = PlaceMapper.toDomain(entity);
                    String dedupKey = buildDedupKey(mappedPlace);
                    if (!seenIds.contains(entity.id)
                            && !seenKeys.contains(dedupKey)) {
                        combinedResults.add(mappedPlace);
                        seenIds.add(entity.id);
                        seenKeys.add(dedupKey);
                    }
                }
            }

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
            Place normalizedPlace = normalizePlaceForCache(place);
            if (normalizedPlace == null) {
                Log.w(TAG, "Cannot persist place with invalid/missing coordinates");
                return;
            }
            
            PlaceEntity existing = placeDao.getByIdSync(normalizedPlace.id,
                    activeUserId);

            PlaceEntity entity = PlaceMapper.toEntity(normalizedPlace,
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

            enforceLocalCacheLimit();

            String operation;
            if (isDelete) {
                operation = "DELETE";
            } else if (existing == null || existing.deleted) {
                operation = "CREATE";
            } else {
                operation = "UPDATE";
            }

            PlaceDto payload = PlaceMapper.toDto(normalizedPlace, activeUserId);
            payload.placeSource = entity.placeSource;
            payload.persistedByAction = action;
            payload.persistedByUserId = activeUserId;
            payload.serverVersion = entity.serverVersion;
            payload.deleted = entity.deleted;

            if (shouldEnqueueChange(existing, entity, operation)) {
                syncManager.enqueueChange(
                        "place",
                        normalizedPlace.id,
                        operation,
                        UUID.randomUUID().toString(),
                        payload);
                syncManager.syncIfOnline();
            }
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
                dto.placeSource = "osm_geocoder";
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
        String userId = UserPreferences.getUserId(appContext);
        if (userId == null || userId.trim().isEmpty()) {
            return "anonymous";
        }
        return userId.trim();
    }

    private Place normalizePlaceForCache(Place place) {
        if (place == null || place.location == null) {
            return null;
        }

        String placeId = place.id;
        if (placeId == null || placeId.trim().isEmpty()) {
            placeId = UUID.randomUUID().toString();
        }

        String normalizedAddress = place.address;
        if (normalizedAddress == null || normalizedAddress.trim().isEmpty()
                || "Unknown Address".equalsIgnoreCase(normalizedAddress)) {
            normalizedAddress = ADDRESS_UNAVAILABLE;
        }

        return new Place(
                placeId,
                place.name != null ? place.name : "",
                normalizedAddress,
                place.rating,
                place.location
        );
    }

    private boolean shouldEnqueueChange(PlaceEntity existing,
                                        PlaceEntity updated,
                                        String operation) {
        if (existing == null) {
            return true;
        }
        if ("DELETE".equalsIgnoreCase(operation)) {
            return !existing.deleted;
        }
        if (!safeEquals(existing.name, updated.name)) {
            return true;
        }
        if (!safeEquals(existing.address, updated.address)) {
            return true;
        }
        if (Double.compare(existing.latitude, updated.latitude) != 0
                || Double.compare(existing.longitude, updated.longitude) != 0) {
            return true;
        }
        if (Double.compare(existing.rating, updated.rating) != 0) {
            return true;
        }
        return existing.deleted != updated.deleted;
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        // Invalid only if BOTH axes are exactly 0.0 (placeholder coordinates)
        if (latitude == 0.0d && longitude == 0.0d) {
            return false;
        }
        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90d && latitude <= 90d
                && longitude >= -180d && longitude <= 180d;
    }

    private Location resolveDefaultSearchLocation() {
        if (configuredDefaultLatitude != null && configuredDefaultLongitude != null
                && Double.isFinite(configuredDefaultLatitude)
                && Double.isFinite(configuredDefaultLongitude)
                && configuredDefaultLatitude >= -90d && configuredDefaultLatitude <= 90d
                && configuredDefaultLongitude >= -180d && configuredDefaultLongitude <= 180d) {
            return new Location(configuredDefaultLatitude, configuredDefaultLongitude);
        }
        return null;
    }

    private String buildDedupKey(Place place) {
        if (place == null || place.location == null) {
            return "unknown";
        }

        long latBucket = Math.round(place.location.latitude * 100000d);
        long lngBucket = Math.round(place.location.longitude * 100000d);
        String normalizedName = place.name == null
                ? ""
                : place.name.trim().toLowerCase(java.util.Locale.ROOT);

        return normalizedName + "|" + latBucket + "|" + lngBucket;
    }
}

