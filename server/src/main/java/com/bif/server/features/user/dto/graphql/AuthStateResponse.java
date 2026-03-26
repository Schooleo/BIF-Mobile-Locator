package com.bif.server.features.user.dto.graphql;

public record AuthStateResponse(
        boolean authenticated,
        String userId,
        boolean hasProfile
) {
}
