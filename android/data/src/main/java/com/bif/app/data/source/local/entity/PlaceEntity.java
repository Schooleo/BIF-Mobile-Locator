package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;

@Entity(tableName = "places", primaryKeys = { "ownerUserId", "id" })
public class PlaceEntity {
    @NonNull
    public String ownerUserId;
    @NonNull
    public String id;
    public String name;
    public String address;
    public double rating;
    public double latitude;
    public double longitude;
    public String tags;
    public String placeSource;
    public String persistedByAction;
    public long serverVersion;
    public boolean deleted;
    public long lastSyncedAt;

    public PlaceEntity() {
        ownerUserId = "";
        id = "";
    }
}
