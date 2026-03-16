package com.bif.server.features.sync.models;

import lombok.Data;

import java.util.List;

@Data
public class SyncRequest {
    private String userId;
    private String deviceId;
    private long lastPulledVersion;
    private List<SyncChange> pushedChanges;
}