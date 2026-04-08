package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.network.dto.trip.TripPlanDto;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.google.gson.Gson;

public class TripSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "TripSyncEntityHandler";
    private static final int MAX_TRIPS_PER_GROUP = 30;

    private final TripDao tripDao;
    private final Gson gson;

    public TripSyncEntityHandler(TripDao tripDao, Gson gson) {
        this.tripDao = tripDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "trip_plan";
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
            TripPlanEntity existing = tripDao.getTripByIdSync(change.entityId);
            TripPlanEntity entity = existing != null ? existing : new TripPlanEntity();
            entity.id = change.entityId;
            entity.deleted = true;
            entity.serverVersion = change.serverVersion;
            tripDao.upsertTrip(entity);
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            TripPlanDto payload = gson.fromJson(change.payload, TripPlanDto.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            // Check if current user is in the participant list
            boolean userIsParticipant = false;
            if (activeUserId != null && !activeUserId.isEmpty() && payload.participantIds != null) {
                for (String participantId : payload.participantIds) {
                    if (participantId != null && activeUserId.equals(participantId.trim())) {
                        userIsParticipant = true;
                        break;
                    }
                }
            }

            // If user is not a participant, mark the trip as deleted
            boolean shouldMarkDeleted = payload.deleted
                    || "DELETE".equalsIgnoreCase(change.operation)
                    || !userIsParticipant;

            TripPlanEntity existing = tripDao.getTripByIdSync(payload.id);
            TripPlanEntity entity = existing != null ? existing : new TripPlanEntity();
            entity.id = payload.id;
            entity.groupId = payload.groupId;
            entity.title = payload.title;
            entity.description = payload.description;
            entity.startAt = parseInstant(payload.startAt);
            entity.endAt = parseInstant(payload.endAt);
            entity.serverVersion = Math.max(payload.serverVersion, change.serverVersion);
            entity.deleted = shouldMarkDeleted;
            tripDao.upsertTrip(entity);
            if (payload.participantIds != null && userIsParticipant) {
                tripDao.replaceTripMembersFromParticipantIds(entity.id,
                        payload.participantIds,
                        activeUserId);
            }
            enforceGroupCap(entity.groupId);
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled trip plan change", e);
        }
    }

    private void enforceGroupCap(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return;
        }

        int activeCount = tripDao.countActiveTripsByGroup(groupId);
        if (activeCount > MAX_TRIPS_PER_GROUP) {
            tripDao.evictOldestTripsByGroup(groupId,
                    activeCount - MAX_TRIPS_PER_GROUP);
        }
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

