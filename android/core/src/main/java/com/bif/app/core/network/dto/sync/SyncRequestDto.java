package com.bif.app.core.network.dto.sync;

import java.util.List;

public class SyncRequestDto {
    public String userId;
    public String deviceId;
    public long lastPulledVersion;
    public List<SyncChangeDto> pushedChanges;
}

