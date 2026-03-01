package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class FavoriteEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double latitude;
    public double longitude;
    public String address;
    public String description;
    public String notes;
    public int rating;
    public String imagePath;
}
