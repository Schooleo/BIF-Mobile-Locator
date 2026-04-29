package com.bif.server.features.auth.dto.rest;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword
) {
}
