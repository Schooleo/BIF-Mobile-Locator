package com.bif.server.features.sync.models;

import lombok.Data;

import java.util.List;

@Data
public class SyncResponse {
    private long currentServerVersion;
    private List<SyncChange> pulledChanges;
}