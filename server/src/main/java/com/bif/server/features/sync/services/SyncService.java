package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.sync.models.SyncConflict;
import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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

    public SyncService(SyncVersionService syncVersionService,
                       SyncChangeRepository syncChangeRepository,
                       List<SyncEntityHandler> handlers) {
        this.syncVersionService = syncVersionService;
        this.syncChangeRepository = syncChangeRepository;
        this.handlersByEntityType = new HashMap<>();
        for (SyncEntityHandler handler : handlers) {
            handlersByEntityType.put(handler.entityType().toLowerCase(),
                    handler);
        }
    }

    public SyncResponse sync(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<SyncConflict> conflicts = new ArrayList<>();
        Set<String> pushedClientChangeIds = new HashSet<>();

        // Phase 1: Process pushed changes from client
        if (request.getPushedChanges() != null) {
            for (SyncChange pushed : request.getPushedChanges()) {
                if (pushed.getClientChangeId() != null
                        && !pushed.getClientChangeId().isBlank()) {
                    pushedClientChangeIds.add(pushed.getClientChangeId());
                }
                processPushedChange(pushed, request.getUserId(), conflicts);
            }
        }

        // Phase 2: Pull changes since client's last known version
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
        return response;
    }

    private void processPushedChange(SyncChange pushed, String userId,
                                     List<SyncConflict> conflicts) {
        // Idempotency: skip if already processed
        if (pushed.getClientChangeId() != null) {
            Optional<SyncChangeEntry> existing = syncChangeRepository
                    .findByClientChangeId(pushed.getClientChangeId());
            if (existing.isPresent()) {
                return; // Already processed, skip
            }
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

        // Apply the pushed mutation before recording it in the log.
        long newVersion = syncVersionService.nextVersion();
        String payload = applyEntityMutation(pushed, userId, newVersion);

        // Record the change in the change-log.
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType(pushed.getEntityType());
        entry.setEntityId(pushed.getEntityId());
        entry.setServerVersion(newVersion);
        entry.setOperation(pushed.getOperation());
        entry.setClientChangeId(pushed.getClientChangeId());
        entry.setPayload(payload);
        entry.setUserId(userId);
        entry.setTimestamp(Instant.now());
        syncChangeRepository.save(entry);
    }

    private String applyEntityMutation(SyncChange pushed, String userId,
                                       long newVersion) {
        if (pushed.getEntityType() == null) {
            return pushed.getPayload();
        }

        SyncEntityHandler handler = handlersByEntityType.get(
                pushed.getEntityType().toLowerCase());
        if (handler == null) {
            return pushed.getPayload();
        }

        return handler.applyPushedChange(pushed, userId, newVersion);
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
}
