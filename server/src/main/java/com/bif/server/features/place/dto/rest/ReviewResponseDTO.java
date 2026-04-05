package com.bif.server.features.place.dto.rest;

import java.time.LocalDateTime;

public record ReviewResponseDTO(
        String id,
        String placeId,
        String userId,
        String userName,
        int stars,
        String comment,
        LocalDateTime createdAt
) {}
