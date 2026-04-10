package com.bif.server.features.place.dto.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewDTO(
        @Min(1)
        @Max(5)
        int stars,
        String comment
) {
}
