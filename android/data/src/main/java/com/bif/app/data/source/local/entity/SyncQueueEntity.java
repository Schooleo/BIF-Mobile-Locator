package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "sync_queue",
    indices = {@Index(value = {"userId", "entityType", "status"})})
public class SyncQueueEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    @NonNull
    public String userId = "";
    public String clientChangeId;
    public String entityType;
    public String entityId;
    public String operation;
    public String payload;
    public int retryCount;
    public long createdAt;
    public String status;
}
