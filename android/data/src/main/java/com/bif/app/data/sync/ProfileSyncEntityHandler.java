package com.bif.app.data.sync;

import android.content.Context;
import android.util.Log;

import com.bif.app.core.network.dto.profile.ProfileDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ProfileMapper;
import com.bif.app.data.source.local.ProfileDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.google.gson.Gson;

public class ProfileSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "ProfileSyncHandler";

    private final ProfileDao profileDao;
    private final Gson gson;
    private final Context appContext;

    public ProfileSyncEntityHandler(ProfileDao profileDao, Gson gson,
                                    Context appContext) {
        this.profileDao = profileDao;
        this.gson = gson;
        this.appContext = appContext;
    }

    @Override
    public String entityType() {
        return "profile";
    }

    @Override
    public String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String) {
            return (String) payload;
        }
        return gson.toJson(payload);
    }

    @Override
    public void applyPulledChange(SyncChangeDto change, String activeUserId) {
        if ("DELETE".equalsIgnoreCase(change.operation)
                && (change.payload == null || change.payload.isEmpty())) {
            ProfileDto tombstone = new ProfileDto();
            tombstone.userId = resolvePayloadUserId(change.entityId,
                    activeUserId);
            tombstone.serverVersion = change.serverVersion;
            tombstone.updatedAt = System.currentTimeMillis();
            tombstone.deleted = true;
            profileDao.upsert(ProfileMapper.fromDto(tombstone,
                    activeUserId));
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            ProfileDto payload = gson.fromJson(change.payload,
                    ProfileDto.class);
            if (payload == null) {
                return;
            }

            payload.userId = resolvePayloadUserId(payload.userId,
                    change.entityId, activeUserId);
            if (payload.userId == null || payload.userId.isEmpty()) {
                return;
            }

            payload.serverVersion = Math.max(payload.serverVersion,
                    change.serverVersion);
            if (payload.updatedAt == 0L) {
                payload.updatedAt = System.currentTimeMillis();
            }
            if ("DELETE".equalsIgnoreCase(change.operation)) {
                payload.deleted = true;
            }

            ProfileEntity merged = ProfileMapper.fromDto(payload,
                    activeUserId);
            if (payload.avatarUrl != null && !payload.avatarUrl.trim().isEmpty()) {
                merged.localImagePath = null;
                merged.uploadStatus = UploadStatus.SYNCED;
            }
            profileDao.upsert(merged);

            if (!payload.deleted && payload.userId.equals(activeUserId)) {
                persistProfileToPreferences(payload);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled profile change", e);
        }
    }

    private String resolvePayloadUserId(String entityId,
                                        String activeUserId) {
        return resolvePayloadUserId(null, entityId, activeUserId);
    }

    private String resolvePayloadUserId(String userId,
                                        String entityId,
                                        String activeUserId) {
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        if (entityId != null && !entityId.trim().isEmpty()) {
            return entityId.trim();
        }
        if (activeUserId != null && !activeUserId.trim().isEmpty()) {
            return activeUserId.trim();
        }
        return null;
    }

    private void persistProfileToPreferences(ProfileDto payload) {
        String userId = safe(payload.userId);
        if (userId.isEmpty()) {
            userId = UserPreferences.getUserId(appContext);
        }
        String displayName = safe(payload.displayName);
        String email = safe(payload.email);
        if (!displayName.isEmpty() || !email.isEmpty()) {
            UserPreferences.saveUserProfile(appContext, userId, displayName, email);
        }
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}

