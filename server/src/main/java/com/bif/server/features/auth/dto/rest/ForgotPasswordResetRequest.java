package com.bif.server.features.auth.dto.rest;

public record ForgotPasswordResetRequest(
        String resetToken,
        String newPassword
) {
}