package com.bif.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ProfileMapper;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.bif.app.data.sync.worker.ImageUploadWorker;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.repository.IProfileRepository;
import com.google.gson.Gson;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class ProfileRepository implements IProfileRepository {

    private final Context appContext;
    private final ProfileDao profileDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final SyncManager syncManager;
    private final ExecutorService executorService;
    private final Gson gson;

    @Inject
    public ProfileRepository(@ApplicationContext Context appContext,
            ProfileDao profileDao,
            SyncQueueDao syncQueueDao,
            AppDatabase appDatabase,
            SyncManager syncManager,
            ExecutorService executorService) {
        this.appContext = appContext;
        this.profileDao = profileDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.syncManager = syncManager;
        this.executorService = executorService;
        this.gson = new Gson();

        String activeUserId = resolveActiveUserId();
        if (!activeUserId.isEmpty()) {
            syncManager.setUserContext(activeUserId, null);
        }
    }

    @Override
    public LocalProfile readLocalProfile() {
        String activeUserId = resolveActiveUserId();
        ProfileEntity localEntity = null;
        if (!activeUserId.isEmpty()) {
            try {
                localEntity = profileDao.getByUserId(activeUserId);
            } catch (IllegalStateException ignored) {
                // Room blocks main-thread queries. Fall back to cached preferences.
                localEntity = null;
            }
        }

        String username = safe(UserPreferences.getUsername(appContext));
        String email = safe(UserPreferences.getEmail(appContext));
        if (localEntity != null) {
            if (localEntity.displayName != null
                    && !localEntity.displayName.trim().isEmpty()) {
                username = localEntity.displayName.trim();
            }
            if (localEntity.email != null
                    && !localEntity.email.trim().isEmpty()) {
                email = localEntity.email.trim();
            }
        }

        return new LocalProfile(
                UserPreferences.isLoggedIn(appContext),
                username,
                email,
                resolveAvatarForUi(localEntity));
    }

    @Override
    public void saveAvatarUri(String avatarUri) {
        String normalizedPath = sanitize(avatarUri);
        if (normalizedPath.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId();
            if (activeUserId.isEmpty()) {
                return;
            }

            syncManager.setUserContext(activeUserId, null);
            final String[] avatarPathForPrefs = new String[1];
            appDatabase.runInTransaction(() -> {
                ProfileEntity existing = profileDao.getByUserId(activeUserId);
                ProfileEntity entity = existing != null ? existing : new ProfileEntity();
                entity.userId = activeUserId;
                entity.displayName = existing != null
                        ? existing.displayName
                        : safe(UserPreferences.getUsername(appContext));
                entity.email = existing != null
                        ? existing.email
                        : safe(UserPreferences.getEmail(appContext));
                entity.avatarLetter = resolveAvatarLetter(entity.displayName,
                        entity.email, existing);
                entity.avatarColor = existing != null ? existing.avatarColor : 0;
                entity.avatarUrl = existing != null ? existing.avatarUrl : null;
                entity.localImagePath = normalizedPath;
                entity.uploadStatus = UploadStatus.PENDING;
                entity.serverVersion = existing != null ? existing.serverVersion : 0;
                entity.updatedAt = System.currentTimeMillis();
                entity.deleted = false;
                profileDao.upsert(entity);
                avatarPathForPrefs[0] = normalizedPath;
            });

            if (avatarPathForPrefs[0] != null) {
                UserPreferences.setAvatarUri(appContext, avatarPathForPrefs[0]);
            }

            ImageUploadWorker.enqueue(appContext);
        });
    }

    @Override
    public void syncProfileMetadata(ProfileCallback callback) {
        if (!UserPreferences.isLoggedIn(appContext)) {
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }

        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId();
            if (!activeUserId.isEmpty()) {
                syncManager.setUserContext(activeUserId, null);
            }

            try {
                Object syncResponse = syncManager.sync();
                if (syncResponse != null || !syncManager.isOnline()) {
                    if (callback != null) {
                        callback.onSuccess();
                    }
                } else if (callback != null) {
                    callback.onFailure();
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure();
                }
            }
        });
    }

    @Override
    public void updateProfile(String updatedUsername, ProfileCallback callback) {
        String normalizedName = sanitize(updatedUsername);
        if (normalizedName.isEmpty()) {
            if (callback != null) {
                callback.onFailure();
            }
            return;
        }

        if (!UserPreferences.isLoggedIn(appContext)) {
            if (callback != null) {
                callback.onFailure();
            }
            return;
        }

        executorService.execute(() -> {
            String activeUserId = resolveActiveUserId();
            if (activeUserId.isEmpty()) {
                if (callback != null) {
                    callback.onFailure();
                }
                return;
            }

            syncManager.setUserContext(activeUserId, null);
            try {
                appDatabase.runInTransaction(() -> {
                    ProfileEntity existing = profileDao.getByUserId(
                            activeUserId);
                    ProfileEntity entity = existing != null
                            ? existing
                            : new ProfileEntity();
                    entity.userId = activeUserId;
                    entity.displayName = normalizedName;
                    entity.email = resolveEmail(existing);
                    entity.avatarLetter = resolveAvatarLetter(normalizedName,
                            entity.email, existing);
                    entity.avatarColor = existing != null
                            ? existing.avatarColor
                            : 0;
                    entity.avatarUrl = existing != null
                            ? existing.avatarUrl
                            : null;
                    entity.localImagePath = existing != null
                            ? existing.localImagePath
                            : null;
                    entity.uploadStatus = existing != null
                            ? existing.uploadStatus
                            : UploadStatus.SYNCED;
                    entity.serverVersion = existing != null
                            ? existing.serverVersion
                            : 0;
                    entity.updatedAt = System.currentTimeMillis();
                    entity.deleted = false;

                    profileDao.upsert(entity);
                    persistProfileToPreferences(entity);

                    SyncQueueEntity syncEntry = createSyncEntry(
                            activeUserId,
                            "profile",
                            entity.userId,
                            "UPDATE",
                            ProfileMapper.toDto(entity));
                    syncQueueDao.enqueue(syncEntry);
                });

                syncManager.syncIfOnline();
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onFailure();
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

    private void persistProfileToPreferences(@NonNull ProfileEntity entity) {
        String displayName = safe(entity.displayName);
        String email = safe(entity.email);
        if (!displayName.isEmpty() || !email.isEmpty()) {
            UserPreferences.saveUserProfile(appContext, entity.userId,
                    displayName, email);
        }
    }

    private String resolveEmail(ProfileEntity existing) {
        if (existing != null && existing.email != null
                && !existing.email.trim().isEmpty()) {
            return existing.email.trim();
        }
        return safe(UserPreferences.getEmail(appContext));
    }

    private String resolveAvatarLetter(String displayName,
            String email,
            ProfileEntity existing) {
        String trimmedName = safe(displayName);
        if (!trimmedName.isEmpty()) {
            return trimmedName.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        String trimmedEmail = safe(email);
        if (!trimmedEmail.isEmpty()) {
            return trimmedEmail.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        if (existing != null && existing.avatarLetter != null
                && !existing.avatarLetter.trim().isEmpty()) {
            return existing.avatarLetter.trim();
        }

        return "G";
    }

    private String resolveActiveUserId() {
        String userId = UserPreferences.getUserId(appContext);
        if (userId == null) {
            return "";
        }
        return userId.trim();
    }

    private String resolveAvatarForUi(ProfileEntity localEntity) {
        if (localEntity != null) {
            boolean hasLocal = localEntity.localImagePath != null
                    && !localEntity.localImagePath.trim().isEmpty();
            if (hasLocal && localEntity.uploadStatus != UploadStatus.SYNCED) {
                return localEntity.localImagePath.trim();
            }
            if (localEntity.avatarUrl != null && !localEntity.avatarUrl.trim().isEmpty()) {
                return localEntity.avatarUrl.trim();
            }
            if (hasLocal) {
                return localEntity.localImagePath.trim();
            }
        }
        return safe(UserPreferences.getAvatarUri(appContext));
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}

