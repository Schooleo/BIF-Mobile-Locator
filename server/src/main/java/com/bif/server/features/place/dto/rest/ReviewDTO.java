package com.bif.server.features.place.dto.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewDTO(
        @Min(1)
        @Max(5)
        int stars,
        @NotBlank
        String comment
) {
}
