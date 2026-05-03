package com.bif.server.features.place.dto.rest;

public record ReviewResponseDTO(
        String id,
        String placeId,
        String userId,
        String userName,
        int stars,
        String comment,
        String externalSource,
        String externalId,
        Double lat,
        Double lng,
        String placeName,
        long createdAt
) {
    public ReviewResponseDTO(
            String id,
            String placeId,
            String userId,
            String userName,
            int stars,
            String comment,
            long createdAt
    ) {
        this(id, placeId, userId, userName, stars, comment, null, null, null, null, null, createdAt);
    }
}
