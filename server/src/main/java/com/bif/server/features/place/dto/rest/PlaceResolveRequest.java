package com.bif.server.features.place.dto.rest;

public record PlaceResolveRequest(
        String externalSource,
        String externalId,
        Double lat,
        Double lng,
        String name
) {

    public PlaceResolveRequest {
        requireNotBlank(externalSource, "externalSource");
        requireNotBlank(externalId, "externalId");
        requireNotBlank(name, "name");

        requireNotNull(lat, "lat");
        requireNotNull(lng, "lng");

        if (!Double.isFinite(lat)) {
            throw new IllegalArgumentException("lat must be a finite number");
        }
        if (!Double.isFinite(lng)) {
            throw new IllegalArgumentException("lng must be a finite number");
        }

        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng must be between -180 and 180");
        }
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void requireNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
    }
}
