package com.bif.app.core.network.dto.place;

public class PlaceReviewDto {
    public String placeId;
    public String userId;
    public String userName;
    public int rating;
    public int stars;  // Server uses 'stars', Android uses 'rating'
    public String comment;
    public String createdAt;
    
    // Sync metadata from server
    public long serverVersion;
    public String updatedAt; // ISO format

    /** Returns the effective star count, preferring 'stars' over 'rating'. */
    public int getEffectiveStars() {
        return stars > 0 ? stars : rating;
    }
}

