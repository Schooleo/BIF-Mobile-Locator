package com.bif.server.features.auth.dto.rest;

public record ForgotPasswordVerifyOtpRequest(
        String email,
        String otp
) {
}