package com.bif.app.core.network.dto;

public class FavoriteDto {
    public String id;
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
}
