package com.bif.server.features.auth.dto.rest;

public record AuthUserResponse(
        String id,
        String username,
        String email
) {
}
