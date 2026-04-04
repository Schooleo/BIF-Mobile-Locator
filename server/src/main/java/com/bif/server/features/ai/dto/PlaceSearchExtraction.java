package com.bif.server.features.ai.dto;

import java.util.List;
import java.util.Objects;

public record PlaceSearchExtraction(
        List<String> keywords,
        String category,
        String vibe) {

    public PlaceSearchExtraction {
        keywords = keywords == null
                ? List.of()
                : keywords.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList();
        category = normalize(category);
        vibe = normalize(vibe);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
