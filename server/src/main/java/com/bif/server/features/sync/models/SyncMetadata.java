package com.bif.server.features.sync.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "sync_metadata")
public class SyncMetadata {
    @Id
    private String id;
    private long currentVersion;
}
