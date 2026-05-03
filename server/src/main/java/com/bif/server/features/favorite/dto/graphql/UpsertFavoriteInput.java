package com.bif.server.features.favorite.dto.graphql;

import com.bif.server.common.models.Location;

public record UpsertFavoriteInput(
        String id,
        String externalSource,
        String externalId,
        String placeName,
        String name,
        Location location,
        String address,
        String description,
        String notes,
        int rating,
        String imagePath,
        String userId
) {
}
