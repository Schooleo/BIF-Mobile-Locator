package com.bif.app.core.network.dto;

public class SyncConflictDto {
    public String entityType;
    public String entityId;
    public String clientChangeId;
    public long clientVersion;
    public long serverVersion;
    public String resolution;
}
