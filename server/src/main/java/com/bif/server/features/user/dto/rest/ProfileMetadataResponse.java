package com.bif.server.features.user.dto.rest;

import java.time.Instant;

public record ProfileMetadataResponse(
        String userId,
        String displayName,
        String email,
        String avatarLetter,
        int avatarColor,
        boolean online,
        long serverVersion,
        Instant updatedAt,
        int profileCompletionPercent
) {
}
