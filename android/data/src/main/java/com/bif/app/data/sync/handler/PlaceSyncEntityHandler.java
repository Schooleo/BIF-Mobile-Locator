package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.mapper.PlaceMapper;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.google.gson.Gson;

public class PlaceSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "PlaceSyncHandler";

    private final PlaceDao placeDao;
    private final Gson gson;

    public PlaceSyncEntityHandler(PlaceDao placeDao, Gson gson) {
        this.placeDao = placeDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "place";
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
            PlaceDto tombstone = new PlaceDto();
            tombstone.id = change.entityId;
            tombstone.serverVersion = change.serverVersion;
            tombstone.deleted = true;
            tombstone.persistedByUserId = activeUserId;
            placeDao.upsert(PlaceMapper.fromDto(tombstone, activeUserId));
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            PlaceDto payload = gson.fromJson(change.payload, PlaceDto.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            payload.serverVersion = Math.max(payload.serverVersion,
                    change.serverVersion);
            if ("DELETE".equalsIgnoreCase(change.operation)) {
                payload.deleted = true;
            }

            placeDao.upsert(PlaceMapper.fromDto(payload, activeUserId));
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled place change", e);
        }
    }
}


