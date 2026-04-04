package com.bif.server.features.user.dto.graphql;

import java.time.Instant;

public record ProfileMetadataResponse(
        String userId,
        String displayName,
        String email,
        String avatarLetter,
        String avatarUrl,
        int avatarColor,
        boolean online,
        long serverVersion,
        Instant updatedAt,
        int profileCompletionPercent
) {
}
