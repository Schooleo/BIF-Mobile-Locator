package com.bif.server.common.models;

import lombok.Data;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

@Data
public abstract class SyncDocument {
    private long serverVersion;

    @LastModifiedDate
    private Instant updatedAt;

    private boolean deleted;
    private String lastModifiedBy;
}