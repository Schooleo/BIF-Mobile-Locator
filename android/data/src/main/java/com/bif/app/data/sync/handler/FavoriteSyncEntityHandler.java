package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.favorite.FavoriteDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.google.gson.Gson;

public class FavoriteSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "FavoriteSyncHandler";

    private final FavoriteDao favoriteDao;
    private final Gson gson;

    public FavoriteSyncEntityHandler(FavoriteDao favoriteDao, Gson gson) {
        this.favoriteDao = favoriteDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "favorite";
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
        String resolvedUserId = activeUserId != null
            ? activeUserId.trim()
            : "";
        if (resolvedUserId.isEmpty()) {
            Log.w(TAG, "Missing active user context, deferring pulled favorite change");
            return;
        }

        if ("DELETE".equalsIgnoreCase(change.operation)
                && (change.payload == null || change.payload.isEmpty())) {

            FavoriteEntity local = favoriteDao.findById(change.entityId, resolvedUserId);
            if (local != null && local.pendingSync) {
                return;
            }

            FavoriteDto tombstone = new FavoriteDto();
            tombstone.id = change.entityId;
            tombstone.serverVersion = change.serverVersion;
            tombstone.deleted = true;
            tombstone.userId = resolvedUserId;
            favoriteDao.upsert(FavoriteMapper.fromDto(tombstone, resolvedUserId));
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            FavoriteDto payload = gson.fromJson(change.payload, FavoriteDto.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            payload.serverVersion = Math.max(payload.serverVersion,
                    change.serverVersion);
            if ("DELETE".equalsIgnoreCase(change.operation)) {

                payload.deleted = true;
            }

            FavoriteEntity local = favoriteDao.findById(payload.id, resolvedUserId);
            if (local != null && local.pendingSync) {

                return;
            }

            // Deduplication by placeId
            if (payload.placeId != null && !payload.placeId.trim().isEmpty()) {
                FavoriteEntity existingWithSamePlaceId = favoriteDao.findActiveByPlaceId(payload.placeId.trim(), resolvedUserId);
                if (existingWithSamePlaceId != null && !existingWithSamePlaceId.id.equals(payload.id)) {
                    if (existingWithSamePlaceId.pendingSync) {

                        return;
                    } else {

                        favoriteDao.delete(existingWithSamePlaceId);
                    }
                }
            }

            if (payload.placeId == null
                    && local != null
                    && local.placeId != null
                    && !local.placeId.trim().isEmpty()) {
                payload.placeId = local.placeId;
            }

            if (isBlank(payload.externalSource)
                    && local != null
                    && !isBlank(local.externalSource)) {
                payload.externalSource = local.externalSource;
            }
            if (isBlank(payload.externalId)
                    && local != null
                    && !isBlank(local.externalId)) {
                payload.externalId = local.externalId;
            }
            if (isBlank(payload.placeName)
                    && local != null
                    && !isBlank(local.placeName)) {
                payload.placeName = local.placeName;
            }

            payload.externalSource = coalesceText(
                    payload.externalSource,
                    local != null ? local.externalSource : null,
                    "OSM");
            payload.placeName = coalesceText(
                    payload.placeName,
                    payload.name,
                    payload.address,
                    local != null ? local.placeName : null,
                    local != null ? local.name : null,
                    local != null ? local.address : null);
            payload.externalId = coalesceText(
                    payload.externalId,
                    local != null ? local.externalId : null,
                    payload.placeId,
                    payload.id);

            FavoriteEntity mapped = FavoriteMapper.fromDto(payload, resolvedUserId);
            mapped.pendingSync = false;

            favoriteDao.upsert(mapped);
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled favorite change", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String coalesceText(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}


