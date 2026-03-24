package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_queue")
public class SyncQueueEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String clientChangeId;
    public String entityType;
    public String entityId;
    public String operation;
    public String payload;
    public int retryCount;
    public long createdAt;
    public String status;
}
