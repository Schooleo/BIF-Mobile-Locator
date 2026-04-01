package com.bif.app.data.sync;

import android.util.Log;

import com.bif.app.core.network.dto.favorite.FavoriteDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.FavoriteDao;
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
        if ("DELETE".equalsIgnoreCase(change.operation)
                && (change.payload == null || change.payload.isEmpty())) {
            FavoriteDto tombstone = new FavoriteDto();
            tombstone.id = change.entityId;
            tombstone.serverVersion = change.serverVersion;
            tombstone.deleted = true;
            tombstone.userId = activeUserId;
            favoriteDao.upsert(FavoriteMapper.fromDto(tombstone, activeUserId));
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

            favoriteDao.upsert(FavoriteMapper.fromDto(payload, activeUserId));
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled favorite change", e);
        }
    }
}

