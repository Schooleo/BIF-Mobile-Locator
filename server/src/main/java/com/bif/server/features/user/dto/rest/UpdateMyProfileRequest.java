package com.bif.server.features.user.dto.rest;

public record UpdateMyProfileRequest(
        String name,
        String avatarLetter,
        Integer avatarColor,
        String avatarUrl
) {
}
