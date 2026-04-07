package com.bif.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiPlaceSuggestionResult {
    private final List<AiPlaceSuggestion> places;
    private final List<String> warnings;
    private final String failureCode;

    public AiPlaceSuggestionResult(List<AiPlaceSuggestion> places,
                                   List<String> warnings,
                                   String failureCode) {
        this.places = places == null ? Collections.emptyList() : new ArrayList<>(places);
        this.warnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
        this.failureCode = failureCode;
    }

    public List<AiPlaceSuggestion> getPlaces() {
        return places;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
