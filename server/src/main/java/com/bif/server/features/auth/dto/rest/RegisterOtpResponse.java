package com.bif.server.features.auth.dto.rest;

public record RegisterOtpResponse(
        boolean success,
        String message
) {
}
