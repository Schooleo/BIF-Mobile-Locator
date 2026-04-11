package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.network.dto.trip.TripStopDto;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TripStopSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "TripStopSyncEntityHandler";

    private final TripDao tripDao;
    private final Gson gson;

    public TripStopSyncEntityHandler(TripDao tripDao, Gson gson) {
        this.tripDao = tripDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "trip_stop";
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
        if (change == null) {
            return;
        }

        if ((change.payload == null || change.payload.isEmpty())
                && "DELETE".equalsIgnoreCase(change.operation)) {
            if (change.entityId == null || change.entityId.isEmpty()) {
                return;
            }
            TripStopEntity existing = tripDao.getStopByIdSync(change.entityId);
            if (existing == null) {
                return;
            }
            existing.deleted = true;
            existing.serverVersion = change.serverVersion;
            tripDao.upsertStop(existing);
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            TripStopDto payload = gson.fromJson(change.payload, TripStopDto.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            hydrateFlatLocationFallback(payload, change.payload);

            TripStopEntity existing = tripDao.getStopByIdSync(payload.id);
            long incomingVersion = Math.max(payload.serverVersion, change.serverVersion);
            if (existing != null && existing.serverVersion > incomingVersion) {
                return;
            }
            TripStopEntity entity = existing != null ? existing : new TripStopEntity();
            entity.id = payload.id;
            entity.tripId = payload.tripId;
            entity.title = payload.title;
            String incomingAddress = payload.address == null ? "" : payload.address.trim();
            if (incomingAddress.isEmpty() && existing != null && existing.address != null) {
                incomingAddress = existing.address.trim();
            }
            if (incomingAddress.isEmpty() && payload.note != null && !payload.note.trim().isEmpty()) {
                incomingAddress = payload.note.trim();
            }
            entity.address = incomingAddress;
            entity.note = payload.note;
            entity.photoUrl = payload.photoUrl;
            entity.latitude = payload.location != null ? payload.location.latitude : 0d;
            entity.longitude = payload.location != null ? payload.location.longitude : 0d;
            entity.arrivalTime = parseInstant(payload.arrivalTime);
            entity.departureTime = parseInstant(payload.departureTime);
            entity.orderIndex = payload.orderIndex;
            entity.serverVersion = incomingVersion;
            entity.deleted = payload.deleted || "DELETE".equalsIgnoreCase(change.operation);
            if (entity.photoUrl != null
                    && !entity.photoUrl.trim().isEmpty()
                    && !hasPendingLocalUpload(entity)) {
                entity.localImagePath = null;
                entity.uploadStatus = UploadStatus.SYNCED;
            }
            tripDao.upsertStop(entity);
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled trip stop change", e);
        }
    }

    private void hydrateFlatLocationFallback(TripStopDto payload, String rawPayload) {
        if (payload == null || payload.location != null
                || rawPayload == null || rawPayload.trim().isEmpty()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(rawPayload).getAsJsonObject();
            if (!root.has("latitude") && !root.has("longitude")) {
                return;
            }
            com.bif.app.core.network.dto.chat.ChatMessageDto.LocationDto location =
                    new com.bif.app.core.network.dto.chat.ChatMessageDto.LocationDto();
            location.latitude = root.has("latitude") && !root.get("latitude").isJsonNull()
                    ? root.get("latitude").getAsDouble()
                    : 0d;
            location.longitude = root.has("longitude") && !root.get("longitude").isJsonNull()
                    ? root.get("longitude").getAsDouble()
                    : 0d;
            payload.location = location;
        } catch (Exception ignored) {
            // Keep original payload if fallback parsing fails.
        }
    }

    private boolean hasPendingLocalUpload(TripStopEntity entity) {
        if (entity.localImagePath == null || entity.localImagePath.trim().isEmpty()) {
            return false;
        }
        return entity.uploadStatus != UploadStatus.SYNCED;
    }

    private long parseInstant(String value) {
        if (value == null || value.isEmpty()) {
            return 0L;
        }
        try {
            return java.time.Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}


