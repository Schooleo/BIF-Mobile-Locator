package com.bif.server.features.ai.dto.graphql;

import java.util.List;

public record AiTripDraft(
        String title,
        String summary,
        List<AiTripDraftStop> stops) {

    public AiTripDraft {
        title = normalize(title);
        summary = normalize(summary);
        stops = stops == null ? List.of() : List.copyOf(stops);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
