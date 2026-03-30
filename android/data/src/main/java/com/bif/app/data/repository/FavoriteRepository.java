package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.AppDatabase;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.SyncQueueDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.google.gson.Gson;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FavoriteRepository implements IFavoriteRepository {

    private final FavoriteDao favoriteDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final SyncManager syncManager;
    private final ExecutorService executorService;
    private final Gson gson;

    @Inject
    public FavoriteRepository(FavoriteDao favoriteDao,
                              SyncQueueDao syncQueueDao,
                              AppDatabase appDatabase,
                              SyncManager syncManager,
                              ExecutorService executorService) {
        this.favoriteDao = favoriteDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.syncManager = syncManager;
        this.executorService = executorService;
        this.gson = new Gson();
    }

    @Override
    public LiveData<List<Favorite>> searchFavorites(String query) {
        return Transformations.map(favoriteDao.searchFavorites(query), FavoriteMapper::toDomainList);
    }

    @Override
    public LiveData<List<Favorite>> getAllFavorites() {
        return Transformations.map(favoriteDao.getAll(), FavoriteMapper::toDomainList);
    }

    @Override
    public void addFavorite(Favorite favorite) {
        executorService.execute(() -> {
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                favoriteDao.insert(entity);

                Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                favorite.id = syncFavorite.id;

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                        "favorite",
                        syncFavorite.id,
                        "CREATE",
                        FavoriteMapper.toDto(syncFavorite, null)
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void updateFavorite(Favorite favorite) {
        executorService.execute(() -> {
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                favoriteDao.update(entity);

                Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                favorite.id = syncFavorite.id;

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                        "favorite",
                        syncFavorite.id,
                        "UPDATE",
                        FavoriteMapper.toDto(syncFavorite, null)
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
            appDatabase.runInTransaction(() -> {
                List<FavoriteEntity> entities = FavoriteMapper.toEntityList(favorites);
                favoriteDao.updateAll(entities);
                for (FavoriteEntity entity : entities) {
                    Favorite syncFavorite = FavoriteMapper.toDomain(entity);
                    SyncQueueEntity syncEntry = createSyncEntry(
                            "favorite",
                            syncFavorite.id,
                            "UPDATE",
                            FavoriteMapper.toDto(syncFavorite, null)
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
            appDatabase.runInTransaction(() -> {
                FavoriteEntity entity = FavoriteMapper.toEntity(favorite);
                // FIX: SOFT DELETE locally to support offline tombstone sync
                entity.deleted = true;
                favoriteDao.update(entity);

                // Atomic Sync Enqueue
                SyncQueueEntity syncEntry = createSyncEntry(
                        "favorite",
                        favorite.id,
                        "DELETE",
                        null
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
        });
    }

    private SyncQueueEntity createSyncEntry(String entityType, String entityId, String operation, Object payload) {
        SyncQueueEntity entry = new SyncQueueEntity();
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
                // Perform a full sync using existing sync() method
                Object syncResponse = syncManager.sync();
                if (syncResponse != null) {
                    if (callback != null) callback.onSuccess();
                } else if (!syncManager.isOnline()) {
                    // Offline mode is expected in offline-first flow.
                    if (callback != null) callback.onSuccess();
                } else {
                    if (callback != null) callback.onError("Sync failed");
                }
            } catch (Exception e) {
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }
}
