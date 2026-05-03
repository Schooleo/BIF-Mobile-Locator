package com.bif.server.features.auth.dto.rest;

public record LoginRequest(
        String email,
        String password
) {
}
