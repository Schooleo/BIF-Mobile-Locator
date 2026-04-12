package com.bif.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    private static final String ENTITY_TYPE_FAVORITE = "favorite";
    private static final String ANONYMOUS_USER_ID = "anonymous";

    private final FavoriteDao favoriteDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final RestApiService restApiService;
    private final SyncManager syncManager;
    private final ExecutorService executorService;
    private final Context appContext;
    private final Gson gson;

    @Inject
    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              RestApiService restApiService,
                              SyncManager syncManager,
                              ExecutorService executorService,
                              @ApplicationContext Context appContext) {
        this.favoriteDao = favoriteDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.restApiService = restApiService;
        this.syncManager = syncManager;
        this.executorService = executorService;
        this.appContext = appContext;
        this.gson = new Gson();
    }

    // Backward-compatible constructor used by tests.
    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              SyncManager syncManager,
                              ExecutorService executorService) {
        this(favoriteDao, syncQueueDao, appDatabase, null, syncManager,
                executorService, null);
        
        // Validations after delegating constructor
        if (favoriteDao == null) {
            throw new IllegalArgumentException("favoriteDao cannot be null");
        }
        if (syncQueueDao == null) {
            throw new IllegalArgumentException("syncQueueDao cannot be null");
        }
        if (appDatabase == null) {
            throw new IllegalArgumentException("appDatabase cannot be null");
        }
        if (syncManager == null) {
            throw new IllegalArgumentException("syncManager cannot be null");
        }
        if (executorService == null) {
            throw new IllegalArgumentException("executorService cannot be null");
        }
    }

    @Override
    public LiveData<List<Favorite>> searchFavorites(String query) {
        String activeUserId = resolveActiveUserId(appContext);
        return Transformations.map(
                favoriteDao.searchFavorites(activeUserId, query),
                FavoriteMapper::toDomainList);
    }

    @Override
    public LiveData<List<Favorite>> getAllFavorites() {
        String activeUserId = resolveActiveUserId(appContext);
        return Transformations.map(
                favoriteDao.getAll(activeUserId),
                FavoriteMapper::toDomainList);
    }

    @Override
    public void addFavorite(Favorite favorite) {
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                entity.userId = activeUserId;
                entity.pendingSync = true;
                favoriteDao.insert(entity);

                Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                favorite.id = syncFavorite.id;

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                    activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        syncFavorite.id,
                        "CREATE",
                        FavoriteMapper.toDto(syncFavorite, activeUserId)
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
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

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                    activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        syncFavorite.id,
                        "UPDATE",
                        FavoriteMapper.toDto(syncFavorite, activeUserId)
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void updateAllFavorites(List<Favorite> favorites) {
        if (favorites == null) return;
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
                            FavoriteMapper.toDto(syncFavorite, activeUserId)
                    );
                    syncQueueDao.enqueue(syncEntry);
                }
            });
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void deleteFavorite(Favorite favorite) {
        if (favorite == null || favorite.id == null) return;
        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId(appContext);
            applySyncUserContext(activeUserId);
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                // Keep a local tombstone so delete can sync when offline.
                entity.userId = activeUserId;
                entity.deleted = true;
                entity.pendingSync = true;
                favoriteDao.update(entity);

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                    activeUserId,
                        ENTITY_TYPE_FAVORITE,
                        favorite.id,
                        "DELETE",
                        null
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
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

    @Override
    public void refreshFavorites(SyncCallback callback) {
        executorService.execute(() -> {
            try {
                bootstrapFavorites();
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                if (callback != null) {
                    String message = e.getMessage();
                    callback.onError(message != null ? message : "Failed to refresh favorites");
                }
            }
        });
    }

    private void bootstrapFavorites() throws IOException {
        String activeUserId = resolveActiveUserId(appContext);
        if (restApiService == null) {
            return;
        }
        if (ANONYMOUS_USER_ID.equals(activeUserId)) {
            return;
        }

        applySyncUserContext(activeUserId);

        Response<List<FavoriteResponseDto>> response = restApiService
                .getMyFavorites()
                .execute();
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
            List<String> trackedIds = syncQueueDao.getTrackedEntityIds(activeUserId,
                    ENTITY_TYPE_FAVORITE);
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
            boolean hasServerSnapshot = serverFavorites != null;
            if (serverFavorites != null) {
                for (FavoriteResponseDto dto : serverFavorites) {
                    Favorite mappedDomain = FavoriteMapper.toDomain(dto);
                    if (mappedDomain == null || mappedDomain.id == null
                            || mappedDomain.id.trim().isEmpty()) {
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

            if (!hasServerSnapshot) {
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
        if (activeUserId == null
                || activeUserId.isEmpty()
                || ANONYMOUS_USER_ID.equals(activeUserId)) {
            return;
        }
        syncManager.setUserContext(activeUserId, null);
    }
}

