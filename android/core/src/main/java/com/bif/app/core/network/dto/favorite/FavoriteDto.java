package com.bif.app.core.network.dto.favorite;

public class FavoriteDto {
    public String id;
    public String name;
    public double latitude;
    public double longitude;
    public String address;
    public String description;
    public String notes;
    public int rating;
    // Sync fields
    public String userId;
    public long serverVersion;
    public boolean deleted;
}

