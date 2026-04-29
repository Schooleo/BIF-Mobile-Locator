package com.bif.server.features.auth.dto.rest;

public record RegisterVerifyOtpRequest(
        String email,
        String otp
) {
}
