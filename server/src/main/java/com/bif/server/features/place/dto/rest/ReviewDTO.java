package com.bif.server.features.place.dto.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewDTO(
        @Min(1)
        @Max(5)
        int stars,
        String comment,
        long createdAt,
        String externalSource,
        String externalId,
        Double lat,
        Double lng,
        String placeName
) {
        public ReviewDTO(int stars, String comment) {
                this(stars, comment, 0L, null, null, null, null, null);
        }
}
