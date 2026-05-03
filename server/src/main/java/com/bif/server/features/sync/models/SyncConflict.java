package com.bif.server.features.sync.models;

import lombok.Data;

@Data
public class SyncConflict {
    private String entityType;
    private String entityId;
    private String clientChangeId;
    private long clientVersion;
    private long serverVersion;
    private String resolution;
}
