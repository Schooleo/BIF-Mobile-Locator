package com.bif.server.features.favorite.dto.rest;

import com.bif.server.common.models.Location;

public record UpsertMyFavoriteRequest(
        String id,
        String name,
        Location location,
        String address,
        String description,
        String notes,
        int rating,
        String imagePath
) {
}
