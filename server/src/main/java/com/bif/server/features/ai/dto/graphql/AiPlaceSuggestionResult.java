package com.bif.server.features.ai.dto.graphql;

import com.bif.server.features.place.models.Place;

import java.util.List;

public record AiPlaceSuggestionResult(
        List<Place> places,
        List<String> extractedKeywords,
        String category,
        String vibe,
        List<String> searchQueries,
        String locationHint,
        List<String> warnings,
        AiFailureCode failureCode) {

    public AiPlaceSuggestionResult(
            List<Place> places,
            List<String> extractedKeywords,
            String category,
            String vibe,
            List<String> warnings,
            AiFailureCode failureCode) {
        this(places, extractedKeywords, category, vibe, List.of(), null, warnings, failureCode);
    }

    public AiPlaceSuggestionResult {
        places = places == null ? List.of() : List.copyOf(places);
        extractedKeywords = extractedKeywords == null
                ? List.of()
                : List.copyOf(extractedKeywords);
        searchQueries = searchQueries == null
                ? List.of()
                : List.copyOf(searchQueries);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
