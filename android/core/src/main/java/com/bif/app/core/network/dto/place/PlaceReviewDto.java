package com.bif.app.core.network.dto.place;

public class PlaceReviewDto {
    public String placeId;
    public String userId;
    public String userName;
    public int stars;
    public String comment;
    public long createdAt;

    // Place identity metadata for stable ID resolution across sync boundaries.
    public String externalSource;
    public String externalId;
    public Double lat;
    public Double lng;
    public String placeName;
    
    // Sync metadata from server
    public long serverVersion;
    public String updatedAt; // ISO format
}

