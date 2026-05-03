package com.bif.server.features.user.dto.graphql;

public record UpdateMyProfileInput(
        String name,
        String avatarLetter,
        Integer avatarColor,
        String avatarUrl
) {
}
