package com.bif.server.features.ai.dto;

import java.util.List;

public record GeneratedItinerary(
        String title,
        String summary,
        List<GeneratedStop> stops) {

    public GeneratedItinerary {
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
