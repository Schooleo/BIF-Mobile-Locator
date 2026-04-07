package com.bif.app.core.network.dto.ai;

public class AiSuggestedPlacePayload {
    public final String id;
    public final String name;
    public final String address;
    public final double rating;
    public final int addedToTripCount;
    public final double latitude;
    public final double longitude;

    public AiSuggestedPlacePayload(String id,
                                   String name,
                                   String address,
                                   double rating,
                                   int addedToTripCount,
                                   double latitude,
                                   double longitude) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.addedToTripCount = addedToTripCount;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
