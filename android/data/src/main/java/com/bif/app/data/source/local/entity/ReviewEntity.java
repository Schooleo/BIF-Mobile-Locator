package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

@Entity(
    tableName = "reviews",
    primaryKeys = {"placeId", "userId"},
    indices = {@Index("createdAt")}
)
public class ReviewEntity {
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
        this.placeId = "";
        this.userId = "";
    }
}
