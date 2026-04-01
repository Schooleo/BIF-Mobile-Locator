package com.bif.server.features.route.dto.rest;

import com.fasterxml.jackson.databind.JsonNode;

public record RouteResponse(
        double distanceMeters,
        double durationSeconds,
        JsonNode geometry,
        String profile
) {
}

