package com.bif.server.features.route.dto.rest;

import com.bif.server.common.models.Location;

import java.util.List;

public record RouteRequest(
        List<Location> waypoints,
        String profile
) {
}

