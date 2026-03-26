package com.bif.server.features.user.dto.rest;

public record AuthStateResponse(
        boolean authenticated,
        String userId,
        boolean hasProfile
) {
}
