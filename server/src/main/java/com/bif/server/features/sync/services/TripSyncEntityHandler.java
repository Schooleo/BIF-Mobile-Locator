package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class TripSyncEntityHandler implements SyncEntityHandler {

    private static final int MAX_TRIPS_PER_GROUP = 30;

    private final TripPlanRepository tripPlanRepository;
    private final ObjectMapper objectMapper;

    public TripSyncEntityHandler(TripPlanRepository tripPlanRepository,
                                 ObjectMapper objectMapper) {
        this.tripPlanRepository = tripPlanRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return "trip_plan";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        TripPlanPayload payload = parsePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        String targetId = pushed.getEntityId();
        if ((targetId == null || targetId.isBlank()) && payload != null) {
            targetId = payload.id;
        }
        if (targetId == null || targetId.isBlank()) {
            return pushed.getPayload();
        }

        final String finalTargetId = targetId;
        Optional<TripPlan> existingOpt = tripPlanRepository
                .findById(finalTargetId);
        TripPlan plan = existingOpt.orElseGet(() -> {
            TripPlan created = new TripPlan();
            created.setId(finalTargetId);
            return created;
        });

        if (payload != null) {
            plan.setGroupId(payload.groupId);
            plan.setTitle(payload.title);
            plan.setDescription(payload.description);
            plan.setCoverImageUrl(payload.coverImageUrl);
            plan.setStartAt(payload.startAt);
            plan.setEndAt(payload.endAt);
            plan.setParticipantIds(payload.participantIds);
        }

        boolean deleteRequested = "DELETE".equals(operation)
                || (payload != null && payload.deleted);

        boolean isCreateOperation = existingOpt.isEmpty();
        String groupId = payload != null ? payload.groupId : plan.getGroupId();
        if (isCreateOperation && !deleteRequested
            && groupId != null && !groupId.isBlank()) {
            long groupTripCount = tripPlanRepository
                .countByGroupIdAndDeletedFalse(groupId);
            if (groupTripCount >= MAX_TRIPS_PER_GROUP) {
            TripPlanPayload limitPayload = new TripPlanPayload();
            limitPayload.id = finalTargetId;
            limitPayload.groupId = groupId;
            limitPayload.title = payload != null ? payload.title : null;
            limitPayload.description = payload != null
                ? payload.description : null;
            limitPayload.coverImageUrl = payload != null
                ? payload.coverImageUrl : null;
            limitPayload.startAt = payload != null ? payload.startAt : null;
            limitPayload.endAt = payload != null ? payload.endAt : null;
            limitPayload.participantIds = payload != null
                ? payload.participantIds : null;
            limitPayload.deleted = true;
            limitPayload.serverVersion = newVersion;
            return writePayload(limitPayload);
            }
        }

        plan.setDeleted(deleteRequested);
        plan.setServerVersion(newVersion);
        plan.setLastModifiedBy(userId);
        TripPlan saved = tripPlanRepository.save(plan);

        TripPlanPayload responsePayload = toPayload(saved);
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        Optional<TripPlan> planOpt = tripPlanRepository.findById(entry.getEntityId());
        if (planOpt.isEmpty()) {
            return entry.getPayload();
        }

        TripPlanPayload payload = toPayload(planOpt.get());
        payload.serverVersion = Math.max(payload.serverVersion,
                entry.getServerVersion());
        return writePayload(payload);
    }

    private TripPlanPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, TripPlanPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(TripPlanPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private TripPlanPayload toPayload(TripPlan plan) {
        TripPlanPayload payload = new TripPlanPayload();
        payload.id = plan.getId();
        payload.groupId = plan.getGroupId();
        payload.title = plan.getTitle();
        payload.description = plan.getDescription();
        payload.coverImageUrl = plan.getCoverImageUrl();
        payload.startAt = plan.getStartAt();
        payload.endAt = plan.getEndAt();
        payload.participantIds = plan.getParticipantIds();
        payload.serverVersion = plan.getServerVersion();
        payload.deleted = plan.isDeleted();
        return payload;
    }

    private static class TripPlanPayload {
        public String id;
        public String groupId;
        public String title;
        public String description;
        public String coverImageUrl;
        public Instant startAt;
        public Instant endAt;
        public List<String> participantIds;
        public long serverVersion;
        public boolean deleted;
    }
}
