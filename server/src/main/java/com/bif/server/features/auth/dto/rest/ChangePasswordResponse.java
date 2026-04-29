package com.bif.server.features.auth.dto.rest;

public record ChangePasswordResponse(
    boolean success,
    String message
) {
}
