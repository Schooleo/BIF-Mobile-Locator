package com.bif.locator.domain.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorites")
public class Favorite {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public double latitude;
    public double longitude;
    public String address;
    public String description;
    public String notes;
    public double rating;
    public String imagePath;
}
