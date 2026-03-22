package com.bif.server.features.sync.models;

import lombok.Data;

@Data
public class SyncChange {
    private String entityType;
    private String entityId;
    private long serverVersion;
    private String operation;
    private String clientChangeId;
    private String timestamp;
}