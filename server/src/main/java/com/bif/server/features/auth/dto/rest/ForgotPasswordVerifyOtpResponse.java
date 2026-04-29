package com.bif.server.features.auth.dto.rest;

public record ForgotPasswordVerifyOtpResponse(
        boolean success,
        String resetToken
) {
}