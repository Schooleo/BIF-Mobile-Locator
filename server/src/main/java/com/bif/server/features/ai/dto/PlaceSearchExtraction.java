package com.bif.server.features.ai.dto;

import java.util.List;
import java.util.Objects;

public record PlaceSearchExtraction(
        List<String> searchQueries,
        List<String> keywords,
        String category,
        String vibe,
        String locationHint) {

    public PlaceSearchExtraction(
            List<String> searchQueries,
            List<String> keywords,
            String category,
            String vibe) {
        this(searchQueries, keywords, category, vibe, null);
    }

    public PlaceSearchExtraction     {
        searchQueries = searchQueries == null
                ? List.of()
                : searchQueries.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .toList();
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
        locationHint = normalize(locationHint);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
