package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.sync.models.SyncConflict;
import com.bif.server.features.sync.models.SyncPushResult;
import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class SyncService {

    private final SyncVersionService syncVersionService;
    private final SyncChangeRepository syncChangeRepository;
    private final Map<String, SyncEntityHandler> handlersByEntityType;
    private final GroupRepository groupRepository;
    private final TripPlanRepository tripPlanRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public SyncService(SyncVersionService syncVersionService,
                       SyncChangeRepository syncChangeRepository,
                       GroupRepository groupRepository,
                       TripPlanRepository tripPlanRepository,
                       ChatMessageRepository chatMessageRepository,
                       ObjectMapper objectMapper,
                       List<SyncEntityHandler> handlers) {
        this.syncVersionService = syncVersionService;
        this.syncChangeRepository = syncChangeRepository;
        this.groupRepository = groupRepository;
        this.tripPlanRepository = tripPlanRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
        this.handlersByEntityType = new HashMap<>();
        for (SyncEntityHandler handler : handlers) {
            handlersByEntityType.put(handler.entityType().toLowerCase(),
                    handler);
        }
    }

    SyncService(SyncVersionService syncVersionService,
                SyncChangeRepository syncChangeRepository,
                List<SyncEntityHandler> handlers) {
        this(syncVersionService, syncChangeRepository, null, null, null, null,
                handlers);
    }

    public SyncResponse sync(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<SyncConflict> conflicts = new ArrayList<>();
        List<SyncPushResult> pushResults = new ArrayList<>();
        Set<String> pushedClientChangeIds = new HashSet<>();

        // Phase 1: Process pushed changes from client
        if (request.getPushedChanges() != null) {
            for (SyncChange pushed : request.getPushedChanges()) {
                if (pushed.getClientChangeId() != null
                        && !pushed.getClientChangeId().isBlank()) {
                    pushedClientChangeIds.add(pushed.getClientChangeId());
                }
                pushResults.add(processPushedChange(pushed, request.getUserId(),
                        conflicts));
            }
        }

        // Phase 2: Pull changes since client's last known version.
        // NOTE: pulls remain scoped to the requesting user. Collaborative
        // propagation for shared entities (group/trip/chat) is intentionally
        // left unchanged in this sweep because widening fanout safely would
        // require additional audience metadata on SyncChangeEntry.
        long baseline = Math.max(0, request.getLastPulledVersion());
        List<SyncChangeEntry> entries = syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        request.getUserId(), baseline);

        List<SyncChange> pulledChanges = new ArrayList<>();
        for (SyncChangeEntry entry : entries) {
            if (entry.getClientChangeId() != null
                    && pushedClientChangeIds.contains(entry.getClientChangeId())) {
                continue;
            }

            SyncChange change = new SyncChange();
            change.setEntityType(entry.getEntityType());
            change.setEntityId(entry.getEntityId());
            change.setServerVersion(entry.getServerVersion());
            change.setOperation(entry.getOperation());
            change.setClientChangeId(entry.getClientChangeId());
            change.setTimestamp(entry.getTimestamp() != null
                    ? entry.getTimestamp().toString() : null);
            change.setPayload(resolvePayload(entry));
            pulledChanges.add(change);
        }

        response.setCurrentServerVersion(syncVersionService.getCurrentVersion());
        response.setPulledChanges(pulledChanges);
        response.setConflicts(conflicts.isEmpty() ? null : conflicts);
        response.setPushResults(pushResults.isEmpty() ? null : pushResults);
        return response;
    }

    private SyncPushResult processPushedChange(SyncChange pushed, String userId,
                                               List<SyncConflict> conflicts) {
        String clientChangeId = pushed != null ? pushed.getClientChangeId() : null;
        if (pushed == null) {
            return new SyncPushResult(null,
                    SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                    "NULL_CHANGE");
        }
        if (clientChangeId == null || clientChangeId.isBlank()) {
            return new SyncPushResult(null,
                    SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                    "MISSING_CLIENT_CHANGE_ID");
        }
        // Idempotency: skip if already processed
        Optional<SyncChangeEntry> existing = syncChangeRepository
                .findByClientChangeId(clientChangeId);
        if (existing.isPresent()) {
            return new SyncPushResult(clientChangeId,
                    SyncPushApplyResult.STATUS_ALREADY_APPLIED,
                    "DUPLICATE_CLIENT_CHANGE_ID");
        }

        // Conflict detection: only compare against latest version for same
        // user + entityType + entityId.
        Optional<SyncChangeEntry> latestEntityChange =
                findLatestForSameEntity(userId, pushed);
        if (pushed.getServerVersion() > 0
                && latestEntityChange.isPresent()
                && pushed.getServerVersion()
                < latestEntityChange.get().getServerVersion()) {
            // LWW: accept the change but report the conflict
            SyncConflict conflict = new SyncConflict();
            conflict.setEntityType(pushed.getEntityType());
            conflict.setEntityId(pushed.getEntityId());
            conflict.setClientChangeId(pushed.getClientChangeId());
            conflict.setClientVersion(pushed.getServerVersion());
            conflict.setServerVersion(latestEntityChange
                    .get().getServerVersion());
            conflict.setResolution("SERVER_WINS");
            conflicts.add(conflict);
        }

        SyncPushApplyResult applyResult = applyEntityMutation(pushed, userId);

        // Record the change in the change-log.
        if (applyResult.shouldPersistChangeLog()) {
            persistChangeLogEntries(pushed, userId, clientChangeId, applyResult);
        }

        return new SyncPushResult(clientChangeId, applyResult.getStatus(),
                applyResult.getReasonCode());
    }

    private SyncPushApplyResult applyEntityMutation(SyncChange pushed,
                                                    String userId) {
        if (pushed.getEntityType() == null) {
            return SyncPushApplyResult.rejectedValidation(
                    "MISSING_ENTITY_TYPE");
        }

        SyncEntityHandler handler = handlersByEntityType.get(
                pushed.getEntityType().toLowerCase());
        if (handler == null) {
            return SyncPushApplyResult.rejectedValidation(
                    "UNSUPPORTED_ENTITY_TYPE");
        }

        try {
            return handler.applyPushedChangeResult(pushed, userId,
                    syncVersionService::nextVersion);
        } catch (RuntimeException ex) {
            return SyncPushApplyResult.retryableFailure("HANDLER_EXCEPTION");
        }
    }

    private String resolvePayload(SyncChangeEntry entry) {
        if (entry.getEntityType() == null) {
            return entry.getPayload();
        }

        SyncEntityHandler handler = handlersByEntityType.get(
                entry.getEntityType().toLowerCase());
        if (handler == null) {
            return entry.getPayload();
        }

        return handler.resolvePayload(entry);
    }

    private Optional<SyncChangeEntry> findLatestForSameEntity(
            String userId, SyncChange pushed) {
        if (userId == null || pushed.getEntityType() == null
                || pushed.getEntityId() == null) {
            return Optional.empty();
        }

        return syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        userId, pushed.getEntityType(), pushed.getEntityId());
    }

    private void persistChangeLogEntries(SyncChange pushed,
                                         String initiatingUserId,
                                         String clientChangeId,
                                         SyncPushApplyResult applyResult) {
        Instant timestamp = Instant.now();
        SyncChangeEntry primary = buildEntry(
                pushed,
                applyResult.getServerVersion(),
                clientChangeId,
                applyResult.getPayload(),
                initiatingUserId,
                timestamp
        );
        syncChangeRepository.save(primary);

        for (String audienceUserId : resolveAudienceUserIds(pushed, applyResult, initiatingUserId)) {
            if (audienceUserId == null || audienceUserId.isBlank()
                    || audienceUserId.equals(initiatingUserId)) {
                continue;
            }
            SyncChangeEntry fanout = buildEntry(
                    pushed,
                    applyResult.getServerVersion(),
                    null,
                    applyResult.getPayload(),
                    audienceUserId,
                    timestamp
            );
            syncChangeRepository.save(fanout);
        }
    }

    private SyncChangeEntry buildEntry(SyncChange pushed,
                                       Long serverVersion,
                                       String clientChangeId,
                                       String payload,
                                       String userId,
                                       Instant timestamp) {
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType(pushed.getEntityType());
        entry.setEntityId(pushed.getEntityId());
        entry.setServerVersion(serverVersion);
        entry.setOperation(pushed.getOperation());
        entry.setClientChangeId(clientChangeId);
        entry.setPayload(payload);
        entry.setUserId(userId);
        entry.setTimestamp(timestamp);
        return entry;
    }

    private Set<String> resolveAudienceUserIds(SyncChange pushed,
                                               SyncPushApplyResult applyResult,
                                               String initiatingUserId) {
        Set<String> audience = new LinkedHashSet<>();
        if (initiatingUserId != null && !initiatingUserId.isBlank()) {
            audience.add(initiatingUserId);
        }
        if (pushed == null || pushed.getEntityType() == null) {
            return audience;
        }

        String entityType = pushed.getEntityType();
        switch (entityType) {
            case "group":
                addGroupAudience(resolveStringField(applyResult.getPayload(), "id",
                        pushed.getEntityId()), audience);
                break;
            case "trip_plan":
                addTripAudience(resolveStringField(applyResult.getPayload(), "id",
                        pushed.getEntityId()), audience);
                break;
            case "trip_stop":
                addTripAudience(resolveStringField(applyResult.getPayload(), "tripId",
                        resolveStringField(pushed.getPayload(), "tripId", null)), audience);
                break;
            case "chatMessage":
                addGroupAudience(resolveStringField(applyResult.getPayload(), "groupId",
                        resolveStringField(pushed.getPayload(), "groupId", null)), audience);
                break;
            default:
                break;
        }
        return audience;
    }

    private void addGroupAudience(String groupId, Set<String> audience) {
        if (groupRepository == null || groupId == null || groupId.isBlank()) {
            return;
        }
        Optional<Group> group = groupRepository.findById(groupId);
        if (group.isEmpty()) {
            return;
        }
        if (group.get().getOwnerId() != null && !group.get().getOwnerId().isBlank()) {
            audience.add(group.get().getOwnerId());
        }
        if (group.get().getMemberIds() != null) {
            audience.addAll(group.get().getMemberIds());
        }
    }

    private void addTripAudience(String tripId, Set<String> audience) {
        if (tripPlanRepository == null || tripId == null || tripId.isBlank()) {
            return;
        }
        Optional<TripPlan> tripPlan = tripPlanRepository.findById(tripId);
        if (tripPlan.isEmpty() || tripPlan.get().getParticipantIds() == null) {
            return;
        }
        audience.addAll(tripPlan.get().getParticipantIds());
    }

    private String resolveStringField(String json,
                                      String fieldName,
                                      String fallback) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        } catch (Exception ignored) {
            // Use fallback.
        }
        return fallback;
    }
}
