package com.bif.server.features.ai.dto.graphql;

import com.bif.server.features.place.models.Place;

import java.util.List;

public record AiPlaceSuggestionResult(
        List<Place> places,
        List<String> extractedKeywords,
        String category,
        String vibe,
        List<String> warnings,
        AiFailureCode failureCode) {

    public AiPlaceSuggestionResult {
        places = places == null ? List.of() : List.copyOf(places);
        extractedKeywords = extractedKeywords == null
                ? List.of()
                : List.copyOf(extractedKeywords);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
