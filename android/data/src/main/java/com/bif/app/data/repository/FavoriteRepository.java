package com.bif.app.data.repository;

import android.content.Context;
import android.location.Address;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import android.content.SharedPreferences;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.AndroidGeocodingDataSource;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class FavoriteRepository implements IFavoriteRepository {

    private static final String TAG = "FavoriteRepository";
    private static final String ENTITY_TYPE_FAVORITE = "favorite";
    private static final String ANONYMOUS_USER_ID = "anonymous";
    private static final String GENERIC_REFRESH_ERROR_MESSAGE = IFavoriteRepository.ERROR_REFRESH_FAILED;

    private final FavoriteDao favoriteDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final PlaceDao placeDao;
    private final AndroidGeocodingDataSource geocodingDataSource;
    private final RestApiService restApiService;
    private final SyncManager syncManager;
    private final ExecutorService executorService;
    private final Context appContext;
    private final Gson gson;
    private final MutableLiveData<String> activeUserIdLiveData;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    @Inject
    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              PlaceDao placeDao,
                              AndroidGeocodingDataSource geocodingDataSource,
                              RestApiService restApiService,
                              SyncManager syncManager,
                              ExecutorService executorService,
                              @ApplicationContext Context appContext) {
        this.favoriteDao = favoriteDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.placeDao = placeDao;
        this.geocodingDataSource = geocodingDataSource;
        this.restApiService = restApiService;
        this.syncManager = syncManager;
        this.executorService = executorService;
        this.appContext = appContext;
        this.gson = new Gson();

        this.activeUserIdLiveData = new MutableLiveData<>(resolveActiveUserId(appContext));
        this.prefListener = (prefs, key) -> {
            if (UserPreferences.KEY_USER_ID.equals(key)) {
                this.activeUserIdLiveData.postValue(resolveActiveUserId(appContext));
            }
        };
        if (appContext != null) {
            appContext.getSharedPreferences(UserPreferences.PREF_NAME, Context.MODE_PRIVATE)
                    .registerOnSharedPreferenceChangeListener(this.prefListener);
        }
    }

    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              RestApiService restApiService,
                              SyncManager syncManager,
                              ExecutorService executorService,
                              @ApplicationContext Context appContext) {
        this(favoriteDao,
                syncQueueDao,
                appDatabase,
                null,
                null,
                restApiService,
                syncManager,
                executorService,
                appContext);
    }

    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              SyncManager syncManager,
                              ExecutorService executorService) {
        this(favoriteDao,
                syncQueueDao,
                appDatabase,
                null,
                null,
                null,
                syncManager,
                executorService,
                null);
    }

    @Override
    public LiveData<List<Favorite>> searchFavorites(String query) {
        return Transformations.switchMap(activeUserIdLiveData, userId ->
                Transformations.map(
                        favoriteDao.searchFavorites(userId, query),
                        FavoriteMapper::toDomainList));
    }

    @Override
    public LiveData<List<Favorite>> getAllFavorites() {
        return Transformations.switchMap(activeUserIdLiveData, userId -> {

            return Transformations.map(
                    favoriteDao.getAll(userId),
                    entities -> {

                        return FavoriteMapper.toDomainList(entities);
                    });
        });
    }

    @Override
    public void addFavorite(Favorite favorite) {

        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);

            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                FavoriteEntity existing = null;
                String syncAction = "CREATE";
                if (favorite.placeId != null && !favorite.placeId.trim().isEmpty()) {
                    existing = favoriteDao.findActiveByPlaceId(favorite.placeId.trim(), activeUserId);
                }

                if (existing != null) {

                    // Update existing with new data but keep ID
                    FavoriteEntity updated = FavoriteMapper.toEntity(favorite);
                    updated.id = existing.id; // Keep original ID
                    updated.serverVersion = existing.serverVersion;
                    updated.userId = activeUserId;
                    updated.pendingSync = true;
                    updated.deleted = false;
                    favoriteDao.update(updated);
                    favorite.id = updated.id;
                    favorite.serverVersion = existing.serverVersion;
                    syncAction = "UPDATE";
                } else {
                    FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                    entity.userId = activeUserId;
                    entity.pendingSync = true;
                    entity.deleted = false;
                    favoriteDao.insert(entity);

                    
                    Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                    favorite.id = syncFavorite.id;
                }

                SyncQueueEntity syncEntry = createSyncEntry(
                        activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        favorite.id,
                    syncAction,
                        FavoriteMapper.toDto(favorite, activeUserId));
                syncQueueDao.enqueue(syncEntry);
            });
            if (syncManager != null) {
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void updateFavorite(Favorite favorite) {
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                entity.userId = activeUserId;
                entity.pendingSync = true;
                favoriteDao.update(entity);

                Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                favorite.id = syncFavorite.id;

                SyncQueueEntity syncEntry = createSyncEntry(
                        activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        syncFavorite.id,
                        "UPDATE",
                        FavoriteMapper.toDto(syncFavorite, activeUserId));
                syncQueueDao.enqueue(syncEntry);
            });
            if (syncManager != null) {
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void updateAllFavorites(List<Favorite> favorites) {
        if (favorites == null) {
            return;
        }
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                List<FavoriteEntity> entities = FavoriteMapper.toEntityList(favorites);
                for (FavoriteEntity entity : entities) {
                    entity.userId = activeUserId;
                    entity.pendingSync = true;
                }
                favoriteDao.updateAll(entities);
                for (FavoriteEntity entity : entities) {
                    Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                    SyncQueueEntity syncEntry = createSyncEntry(
                            activeUserId,
                            ENTITY_TYPE_FAVORITE,
                            syncFavorite.id,
                            "UPDATE",
                            FavoriteMapper.toDto(syncFavorite, activeUserId));
                    syncQueueDao.enqueue(syncEntry);
                }
            });
            if (syncManager != null) {
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void deleteFavorite(Favorite favorite) {
        if (favorite == null || favorite.id == null) {
            return;
        }
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                entity.userId = activeUserId;
                entity.deleted = true;
                entity.pendingSync = true;
                favoriteDao.update(entity);

                SyncQueueEntity syncEntry = createSyncEntry(
                        activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        favorite.id,
                        "DELETE",
                        null);
                syncQueueDao.enqueue(syncEntry);
            });
            if (syncManager != null) {
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void refreshFavorites(SyncCallback callback) {
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            boolean online = syncManager != null && syncManager.isOnline();

            if (canAttemptRemoteRefresh(activeUserId) && !online) {
                reconcileFavoriteMetadata(activeUserId, false);
                if (callback != null) {
                    callback.onOffline();
                }
                return;
            }

            try {
                if (online) {

                    bootstrapFavorites(activeUserId);
                }

                reconcileFavoriteMetadata(activeUserId, online);
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                Log.e(TAG, "refreshFavorites bootstrap failed", e);
                if (callback != null) {
                    callback.onError(GENERIC_REFRESH_ERROR_MESSAGE);
                }
            }
        });
    }

    private SyncQueueEntity createSyncEntry(String ownerUserId,
                                            String entityType,
                                            String entityId,
                                            String operation,
                                            Object payload) {
        SyncQueueEntity entry = new SyncQueueEntity();
        entry.userId = ownerUserId;
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.operation = operation;
        entry.clientChangeId = UUID.randomUUID().toString();
        entry.payload = payload != null ? gson.toJson(payload) : null;
        entry.status = "PENDING";
        entry.retryCount = 0;
        entry.createdAt = System.currentTimeMillis();
        return entry;
    }

    private boolean canAttemptRemoteRefresh(String activeUserId) {
        return restApiService != null && !ANONYMOUS_USER_ID.equals(activeUserId);
    }

    private void bootstrapFavorites(String activeUserId) throws IOException {
        if (!canAttemptRemoteRefresh(activeUserId)) {
            return;
        }

        applySyncUserContext(activeUserId);

        Response<List<FavoriteResponseDto>> response = restApiService.getMyFavorites().execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to refresh favorites. HTTP " + response.code());
        }
        if (response.body() == null) {
            throw new IOException("Failed to refresh favorites. Empty response body");
        }

        mergeServerFavorites(response.body(), activeUserId);
    }

    private void mergeServerFavorites(List<FavoriteResponseDto> serverFavorites,
                                      String activeUserId) {

        appDatabase.runInTransaction(() -> {
            List<String> trackedIds = syncQueueDao.getTrackedEntityIds(activeUserId, ENTITY_TYPE_FAVORITE);
            Set<String> protectedIds = new HashSet<>();
            if (trackedIds != null) {
                protectedIds.addAll(trackedIds);
            }

            List<FavoriteEntity> localFavorites = favoriteDao.getAllSync(activeUserId);
            Map<String, FavoriteEntity> localById = new HashMap<>();
            if (localFavorites != null) {
                for (FavoriteEntity local : localFavorites) {
                    if (local != null && local.id != null && !local.id.trim().isEmpty()) {
                        localById.put(local.id, local);
                    }
                }
            }

            Set<String> serverIds = new HashSet<>();
            if (serverFavorites != null) {
                for (FavoriteResponseDto dto : serverFavorites) {
                    Favorite mappedDomain = FavoriteMapper.toDomain(dto);
                    if (mappedDomain == null || mappedDomain.id == null || mappedDomain.id.trim().isEmpty()) {
                        continue;
                    }

                    String favoriteId = mappedDomain.id.trim();
                    serverIds.add(favoriteId);
                    if (protectedIds.contains(favoriteId)) {
                        continue;
                    }

                    FavoriteEntity merged = FavoriteMapper.toEntity(mappedDomain);
                    merged.userId = activeUserId;
                    merged.deleted = false;
                    merged.pendingSync = false;

                    FavoriteEntity existing = localById.get(favoriteId);
                    if (existing != null) {
                        merged.serverVersion = Math.max(existing.serverVersion, merged.serverVersion);
                    }
                    favoriteDao.upsert(merged);
                }
            }

            if (serverFavorites == null) {
                return;
            }

            for (FavoriteEntity local : localById.values()) {
                if (local == null || local.id == null || local.id.trim().isEmpty()) {
                    continue;
                }
                if (protectedIds.contains(local.id)) {
                    continue;
                }
                if (local.pendingSync) {
                    continue;
                }
                if (!serverIds.contains(local.id)) {

                    favoriteDao.deleteById(local.id, activeUserId);
                }
            }
        });

    }

    private void reconcileFavoriteMetadata(String activeUserId, boolean online) {
        List<FavoriteEntity> favorites = favoriteDao.getAllSync(activeUserId);
        if (favorites == null || favorites.isEmpty()) {
            return;
        }

        List<FavoriteEntity> updatedFavorites = new ArrayList<>();
        for (FavoriteEntity favorite : favorites) {
            FavoriteEntity updated = reconcileFavoriteMetadata(activeUserId, favorite, online);
            if (updated != null) {
                updatedFavorites.add(updated);
            }
        }

        if (updatedFavorites.isEmpty()) {
            return;
        }

        appDatabase.runInTransaction(() -> {
            for (FavoriteEntity updated : updatedFavorites) {
                favoriteDao.update(updated);
                Favorite syncFavorite = FavoriteMapper.toDomain(updated);
                SyncQueueEntity syncEntry = createSyncEntry(
                        activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        syncFavorite.id,
                        "UPDATE",
                        FavoriteMapper.toDto(syncFavorite, activeUserId));
                syncQueueDao.enqueue(syncEntry);
            }
        });

        if (syncManager != null) {
            syncManager.syncIfOnline();
        }
    }

    @Nullable
    private FavoriteEntity reconcileFavoriteMetadata(String activeUserId,
                                                     FavoriteEntity favorite,
                                                     boolean online) {
        if (favorite == null || favorite.deleted || isBlank(favorite.id)) {
            return null;
        }

        FavoriteEntity updated = copyFavoriteEntity(favorite);
        boolean changed = false;

        if (placeDao != null && !isBlank(updated.placeId)) {
            PlaceEntity cachedPlace = placeDao.getByIdSync(updated.placeId, activeUserId);
            if (cachedPlace != null) {
                changed |= applyLocalPlaceMetadata(updated, cachedPlace);
            }
        }

        if (online && geocodingDataSource != null && needsMetadataRefresh(updated)) {
            changed |= applyReverseGeocodeMetadata(updated);
        }

        return changed ? updated : null;
    }

    private boolean applyLocalPlaceMetadata(FavoriteEntity target, PlaceEntity cachedPlace) {
        boolean changed = false;
        String resolvedTitle = resolveDisplayTitle(target.name, cachedPlace.name, cachedPlace.address);
        if (hasMeaningfulText(resolvedTitle)) {
            changed |= updateIfDifferent(target, "name", resolvedTitle);
            changed |= updateIfDifferent(target, "placeName", resolvedTitle);
        }

        if (isPlaceholderText(target.address) && hasMeaningfulText(cachedPlace.address)) {
            String resolvedAddress = normalizeAddress(resolvedTitle, cachedPlace.address);
            if (hasMeaningfulText(resolvedAddress)) {
                changed |= updateIfDifferent(target, "address", resolvedAddress);
            }
        }

        changed |= updateExternalSourceIfDifferent(
                target,
                resolveCanonicalExternalSource(target.externalSource, cachedPlace.placeSource));

        return changed;
    }

    private boolean applyReverseGeocodeMetadata(FavoriteEntity target) {
        if (!isValidCoordinate(target.latitude, target.longitude)) {
            return false;
        }

        List<Address> addresses = geocodingDataSource.reverseGeocodeLocation(target.latitude, target.longitude);
        if (addresses == null || addresses.isEmpty() || addresses.get(0) == null) {
            return false;
        }

        Address address = addresses.get(0);
        String reverseName = trimToNull(address.getFeatureName());
        String reverseAddress = trimToNull(address.getAddressLine(0));

        boolean changed = false;
        String resolvedTitle = resolveDisplayTitle(target.name, reverseName, reverseAddress);
        if (hasMeaningfulText(resolvedTitle)) {
            changed |= updateIfDifferent(target, "name", resolvedTitle);
            changed |= updateIfDifferent(target, "placeName", resolvedTitle);
        }

        if (isPlaceholderText(target.address) && hasMeaningfulText(reverseAddress)) {
            String resolvedAddress = normalizeAddress(resolvedTitle, reverseAddress);
            if (hasMeaningfulText(resolvedAddress)) {
                changed |= updateIfDifferent(target, "address", resolvedAddress);
            }
        }

        changed |= updateExternalSourceIfDifferent(
                target,
                resolveCanonicalExternalSource(target.externalSource, Place.SOURCE_OSM));

        return changed;
    }

    private boolean updateIfDifferent(FavoriteEntity target, String field, String value) {
        String normalizedValue = trimToNull(value);
        if ("name".equals(field)) {
            if (safeEquals(target.name, normalizedValue)) {
                return false;
            }
            target.name = normalizedValue;
            return true;
        }
        if ("placeName".equals(field)) {
            if (safeEquals(target.placeName, normalizedValue)) {
                return false;
            }
            target.placeName = normalizedValue;
            return true;
        }
        if ("address".equals(field)) {
            if (safeEquals(target.address, normalizedValue)) {
                return false;
            }
            target.address = normalizedValue;
            return true;
        }
        return false;
    }

    private boolean updateExternalSourceIfDifferent(FavoriteEntity target, @Nullable String value) {
        String normalizedValue = trimToNull(value);
        if (isPreviewSource(normalizedValue)) {
            return false;
        }
        if (safeEquals(target.externalSource, normalizedValue)) {
            return false;
        }
        target.externalSource = normalizedValue;
        return true;
    }

    private FavoriteEntity copyFavoriteEntity(FavoriteEntity source) {
        FavoriteEntity copy = new FavoriteEntity();
        copy.id = source.id;
        copy.placeId = source.placeId;
        copy.externalSource = source.externalSource;
        copy.externalId = source.externalId;
        copy.placeName = source.placeName;
        copy.name = source.name;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        copy.address = source.address;
        copy.description = source.description;
        copy.notes = source.notes;
        copy.rating = source.rating;
        copy.imagePath = source.imagePath;
        copy.userId = source.userId;
        copy.serverVersion = source.serverVersion;
        copy.deleted = source.deleted;
        copy.pendingSync = true;
        return copy;
    }

    private String resolveDisplayTitle(@Nullable String currentTitle,
                                       @Nullable String preferredTitle,
                                       @Nullable String fallbackAddress) {
        if (hasMeaningfulText(currentTitle)) {
            return currentTitle.trim();
        }
        if (hasMeaningfulText(preferredTitle)) {
            return preferredTitle.trim();
        }
        if (hasMeaningfulText(fallbackAddress)) {
            return fallbackAddress.trim();
        }
        return null;
    }

    private boolean needsMetadataRefresh(FavoriteEntity favorite) {
        return isPlaceholderText(favorite.name)
                || isPlaceholderText(favorite.placeName)
                || isPlaceholderText(favorite.address)
                || isPreviewSource(favorite.externalSource)
                || isBlank(favorite.externalSource);
    }

    @Nullable
    private String resolveCanonicalExternalSource(@Nullable String currentSource,
                                                  @Nullable String preferredSource) {
        String normalizedCurrent = trimToNull(currentSource);
        if (hasCanonicalExternalSource(normalizedCurrent)) {
            return normalizedCurrent;
        }

        String normalizedPreferred = trimToNull(preferredSource);
        if (hasCanonicalExternalSource(normalizedPreferred)) {
            return normalizedPreferred;
        }

        if (normalizedCurrent != null) {
            return normalizedCurrent;
        }
        return normalizedPreferred;
    }

    private boolean hasCanonicalExternalSource(@Nullable String value) {
        return value != null && !isPreviewSource(value);
    }

    private boolean isPreviewSource(@Nullable String value) {
        return value != null && Place.SOURCE_PREVIEW.equalsIgnoreCase(value.trim());
    }

    private boolean isPlaceholderText(@Nullable String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty()
                || normalized.equals("unnamed place")
                || normalized.equals("selected location")
                || normalized.equals("address unavailable")
                || normalized.equals("unknown address");
    }

    private boolean hasMeaningfulText(@Nullable String value) {
        return value != null && !value.trim().isEmpty() && !isPlaceholderText(value);
    }

    private String normalizeAddress(@Nullable String placeName, @Nullable String rawAddress) {
        if (!hasMeaningfulText(rawAddress)) {
            return null;
        }

        String normalized = rawAddress.trim();
        if (hasMeaningfulText(placeName)) {
            String name = placeName.trim();
            if (normalized.equalsIgnoreCase(name)) {
                return null;
            }
            if (normalized.regionMatches(true, 0, name, 0, name.length())) {
                String suffix = normalized.substring(name.length()).trim();
                while (!suffix.isEmpty()) {
                    char first = suffix.charAt(0);
                    if (first == ',' || first == '-' || first == ':' || first == ' ') {
                        suffix = suffix.substring(1).trim();
                        continue;
                    }
                    break;
                }
                if (!suffix.isEmpty()) {
                    normalized = suffix;
                }
            }
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isValidCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90d
                && latitude <= 90d
                && longitude >= -180d
                && longitude <= 180d;
    }

    private boolean safeEquals(@Nullable String left, @Nullable String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveActiveUserId(Context appContext) {
        if (appContext == null) {

            return ANONYMOUS_USER_ID;
        }
        String userId = UserPreferences.getUserId(appContext);
        if (userId == null || userId.isEmpty()) {

            return ANONYMOUS_USER_ID;
        }

        String normalizedUserId = userId.trim();
        if (normalizedUserId.isEmpty()) {

            return ANONYMOUS_USER_ID;
        }

        return normalizedUserId;
    }

    private void applySyncUserContext(String activeUserId) {
        if (syncManager == null
                || activeUserId == null
                || activeUserId.isEmpty()
                || ANONYMOUS_USER_ID.equals(activeUserId)) {
            return;
        }
        syncManager.setUserContext(activeUserId, null);
    }
}
