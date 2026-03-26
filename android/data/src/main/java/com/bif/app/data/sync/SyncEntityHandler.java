package com.bif.app.data.sync;

import com.bif.app.core.network.dto.SyncChangeDto;

public interface SyncEntityHandler {
    String entityType();

    String serializePayload(Object payload);

    void applyPulledChange(SyncChangeDto change, String activeUserId);
}
