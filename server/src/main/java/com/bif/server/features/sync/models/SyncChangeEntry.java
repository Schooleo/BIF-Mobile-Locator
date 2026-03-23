package com.bif.server.features.sync.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "sync_changes")
public class SyncChangeEntry {
    @Id
    private String id;
    private String entityType;
    private String entityId;
    private long serverVersion;
    private String operation;

    @Indexed(unique = true, sparse = true)
    private String clientChangeId;

    private String userId;
    private Instant timestamp;
}
