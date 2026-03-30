package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "social_action_queue")
public class SocialActionQueueEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String scope;
    public String actionType;
    public String payload;
    public String status;
    public int retryCount;
    public long createdAt;
}
