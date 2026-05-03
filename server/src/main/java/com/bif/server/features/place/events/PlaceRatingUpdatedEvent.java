package com.bif.server.features.place.events;

public record PlaceRatingUpdatedEvent(
        String placeId,
        double rating,
        int reviewCount) {
}
