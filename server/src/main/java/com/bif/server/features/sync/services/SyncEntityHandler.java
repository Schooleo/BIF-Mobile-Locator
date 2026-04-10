package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;

import java.util.function.LongSupplier;

public interface SyncEntityHandler {
    String entityType();

    default SyncPushApplyResult applyPushedChangeResult(SyncChange pushed,
                                                        String userId,
                                                        LongSupplier nextVersionSupplier) {
        long newVersion = nextVersionSupplier.getAsLong();
        return SyncPushApplyResult.applied(
                applyPushedChange(pushed, userId, newVersion),
                newVersion
        );
    }

    String applyPushedChange(SyncChange pushed, String userId,
                             long newVersion);

    String resolvePayload(SyncChangeEntry entry);
}
