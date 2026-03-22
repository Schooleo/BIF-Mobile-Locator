package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.*;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SyncService {

    private final SyncVersionService syncVersionService;
    private final SyncChangeRepository syncChangeRepository;

    public SyncService(SyncVersionService syncVersionService,
                       SyncChangeRepository syncChangeRepository) {
        this.syncVersionService = syncVersionService;
        this.syncChangeRepository = syncChangeRepository;
    }

    public SyncResponse sync(SyncRequest request) {
        SyncResponse response = new SyncResponse();
        List<SyncConflict> conflicts = new ArrayList<>();

        // Phase 1: Process pushed changes from client
        if (request.getPushedChanges() != null) {
            for (SyncChange pushed : request.getPushedChanges()) {
                processPushedChange(pushed, request.getUserId(), conflicts);
            }
        }

        // Phase 2: Pull changes since client's last known version
        long baseline = Math.max(0, request.getLastPulledVersion());
        List<SyncChangeEntry> entries = syncChangeRepository
                .findByServerVersionGreaterThanOrderByServerVersionAsc(baseline);

        List<SyncChange> pulledChanges = new ArrayList<>();
        for (SyncChangeEntry entry : entries) {
            SyncChange change = new SyncChange();
            change.setEntityType(entry.getEntityType());
            change.setEntityId(entry.getEntityId());
            change.setServerVersion(entry.getServerVersion());
            change.setOperation(entry.getOperation());
            change.setClientChangeId(entry.getClientChangeId());
            change.setTimestamp(entry.getTimestamp() != null
                    ? entry.getTimestamp().toString() : null);
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

        // Conflict detection: if server version is ahead of what client saw
        long currentVersion = syncVersionService.getCurrentVersion();
        if (pushed.getServerVersion() > 0
                && pushed.getServerVersion() < currentVersion) {
            // LWW: accept the change but report the conflict
            SyncConflict conflict = new SyncConflict();
            conflict.setEntityType(pushed.getEntityType());
            conflict.setEntityId(pushed.getEntityId());
            conflict.setClientChangeId(pushed.getClientChangeId());
            conflict.setClientVersion(pushed.getServerVersion());
            conflict.setServerVersion(currentVersion);
            conflict.setResolution("SERVER_WINS");
            conflicts.add(conflict);
        }

        // Record the change in the change-log
        long newVersion = syncVersionService.nextVersion();
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType(pushed.getEntityType());
        entry.setEntityId(pushed.getEntityId());
        entry.setServerVersion(newVersion);
        entry.setOperation(pushed.getOperation());
        entry.setClientChangeId(pushed.getClientChangeId());
        entry.setUserId(userId);
        entry.setTimestamp(Instant.now());
        syncChangeRepository.save(entry);
    }
}
