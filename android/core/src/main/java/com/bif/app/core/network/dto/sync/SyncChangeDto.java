package com.bif.app.core.network.dto.sync;

public class SyncChangeDto {
    public String entityType;
    public String entityId;
    public long serverVersion;
    public String operation;
    public String clientChangeId;
    public String timestamp;
    public String payload;
}

