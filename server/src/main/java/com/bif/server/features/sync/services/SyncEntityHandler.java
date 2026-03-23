package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;

public interface SyncEntityHandler {
    String entityType();

    String applyPushedChange(SyncChange pushed, String userId,
                             long newVersion);

    String resolvePayload(SyncChangeEntry entry);
}
