package com.bif.server.features.route.dto.rest;

public record RouteResponse(
        double distanceMeters,
        double durationSeconds,
        Object geometry,
        String profile
) {
}

