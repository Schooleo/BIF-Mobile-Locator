package com.bif.server.features.place.dto.rest;

public record PlaceResolveRequest(
        String externalSource,
        String externalId,
        double lat,
        double lng,
        String name
) {
}
