package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.network.dto.trip.TripMemberDto;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.TripMemberCrossRef;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.google.gson.Gson;

public class TripMemberSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "TripMemberSyncHandler";

    private final TripDao tripDao;
    private final Gson gson;

    public TripMemberSyncEntityHandler(TripDao tripDao, Gson gson) {
        this.tripDao = tripDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "trip_member";
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

        TripMemberDto payload = parsePayload(change.payload);
        String tripId = payload != null ? payload.tripId : null;
        String userId = payload != null ? payload.userId : null;

        if ((tripId == null || tripId.trim().isEmpty()
                || userId == null || userId.trim().isEmpty())
                && change.entityId != null) {
            String[] tokens = change.entityId.split(":", 2);
            if (tokens.length == 2) {
                tripId = tokens[0];
                userId = tokens[1];
            }
        }

        if (tripId == null || tripId.trim().isEmpty()
                || userId == null || userId.trim().isEmpty()) {
            return;
        }

        String safeTripId = tripId.trim();
        String safeUserId = userId.trim();

        if ("DELETE".equalsIgnoreCase(change.operation)) {
            tripDao.deleteTripMember(safeTripId, safeUserId);
            return;
        }

        ensureTripParentExists(safeTripId, change.serverVersion);

        String role = payload != null ? payload.role : null;
        if (role == null || role.trim().isEmpty()) {
            role = "COLLABORATOR";
        }
        try {
            tripDao.upsertTripMember(new TripMemberCrossRef(
                    safeTripId,
                    safeUserId,
                    role.trim()
            ));
        } catch (Exception e) {
            // Keep sync resilient to out-of-order payloads and malformed data.
            Log.w(TAG, "Skipping invalid trip member sync for trip=" + safeTripId, e);
        }
    }

    private void ensureTripParentExists(String tripId, long serverVersion) {
        TripPlanEntity existing = tripDao.getTripByIdSync(tripId);
        if (existing != null) {
            return;
        }

        TripPlanEntity placeholder = new TripPlanEntity();
        placeholder.id = tripId;
        placeholder.groupId = tripId;
        placeholder.title = "";
        placeholder.description = "";
        placeholder.startAt = 0L;
        placeholder.endAt = 0L;
        placeholder.serverVersion = Math.max(0L, serverVersion);
        // Hidden placeholder: trip_plan payload will overwrite this on next pull.
        placeholder.deleted = true;
        tripDao.upsertTrip(placeholder);
    }

    private TripMemberDto parsePayload(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(payload, TripMemberDto.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}