package com.bif.app.data.sync.handler;

import com.bif.app.core.network.dto.sync.SyncChangeDto;

public interface SyncEntityHandler {
    String entityType();

    String serializePayload(Object payload);

    void applyPulledChange(SyncChangeDto change, String activeUserId);
}


