package com.bif.server.features.favorite.dto.rest;

import com.bif.server.common.models.Location;

import java.time.Instant;

public record FavoriteResponse(
        String id,
        String name,
        Location location,
        String address,
        String description,
        String notes,
        int rating,
        String imagePath,
        long serverVersion,
        Instant updatedAt
) {
}
