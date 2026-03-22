package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "places")
public class PlaceEntity {
    @PrimaryKey
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
}
