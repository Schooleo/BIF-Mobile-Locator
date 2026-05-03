package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "reviews",
    indices = {
        @Index(value = {"placeId", "userId"}, unique = true),
        @Index("createdAt")
    }
)
public class ReviewEntity {
    @NonNull
    @PrimaryKey
    public String id;
    @NonNull public String placeId;
    @NonNull public String userId;
    public String userName;
    public int stars;
    public String comment;
    public long createdAt;

    // Place identity metadata for stable server-side place resolution.
    public String externalSource;
    public String externalId;
    public Double lat;
    public Double lng;
    public String placeName;

    // Sync metadata
    public long serverVersion;
    public boolean deleted;
    public long lastSyncedAt;
    public boolean pendingSync;

    public ReviewEntity() {
        this.id = "";
        this.placeId = "";
        this.userId = "";
    }
}
