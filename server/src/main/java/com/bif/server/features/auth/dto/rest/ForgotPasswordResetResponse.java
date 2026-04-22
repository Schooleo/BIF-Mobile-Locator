package com.bif.server.features.auth.dto.rest;

public record ForgotPasswordResetResponse(
        boolean success,
        String message
) {
}