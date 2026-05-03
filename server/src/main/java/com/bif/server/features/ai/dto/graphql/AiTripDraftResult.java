package com.bif.server.features.ai.dto.graphql;

import com.bif.server.features.place.models.Place;

import java.util.List;

public record AiTripDraftResult(
        AiTripDraft draft,
        List<Place> candidatePlaces,
    List<String> searchQueries,
        List<String> warnings,
        AiFailureCode failureCode) {

    public AiTripDraftResult {
        candidatePlaces = candidatePlaces == null
                ? List.of()
                : List.copyOf(candidatePlaces);
    searchQueries = searchQueries == null
        ? List.of()
        : List.copyOf(searchQueries);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
