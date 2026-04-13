package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "favorites",
    indices = {@Index(value = {"userId", "deleted"})}
)
public class FavoriteEntity {
    @NonNull
    @PrimaryKey
    public String id;
    public String placeId;
    public String name;
    public double latitude;
    public double longitude;
    public String address;
    public String description;
    public String notes;
    public int rating;
    public String imagePath;

    // Sync fields
    public String userId;
    public long serverVersion;
    public boolean deleted;
    @ColumnInfo(defaultValue = "0")
    public boolean pendingSync;
}
