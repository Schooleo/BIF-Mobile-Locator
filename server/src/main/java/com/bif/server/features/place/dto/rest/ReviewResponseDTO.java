package com.bif.server.features.place.dto.rest;

import java.time.Instant;

public record ReviewResponseDTO(
        String id,
        String placeId,
        String userId,
        String userName,
        int stars,
        String comment,
        Instant createdAt
) {}
