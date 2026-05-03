package com.bif.server.features.auth.dto.rest;

public record RegisterRequest(
        String username,
        String email,
        String password,
        String confirmPassword
) {
}
