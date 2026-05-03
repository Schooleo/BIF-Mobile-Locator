package com.bif.server.features.auth.dto.rest;

public record ForgotPasswordOtpResponse(
        boolean success,
        String message
) {
}