package com.bif.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiPlaceSuggestionResult {
    private final List<AiPlaceSuggestion> places;
    private final List<String> extractedKeywords;
    private final String category;
    private final String vibe;
    private final List<String> searchQueries;
    private final String locationHint;
    private final List<String> warnings;
    private final String failureCode;

    public AiPlaceSuggestionResult(List<AiPlaceSuggestion> places,
                                   List<String> warnings,
                                   String failureCode) {
        this(places, null, null, null, null, null, warnings, failureCode);
    }

    public AiPlaceSuggestionResult(List<AiPlaceSuggestion> places,
                                   List<String> extractedKeywords,
                                   String category,
                                   String vibe,
                                   List<String> searchQueries,
                                   String locationHint,
                                   List<String> warnings,
                                   String failureCode) {
        this.places = places == null ? Collections.emptyList() : new ArrayList<>(places);
        this.extractedKeywords = extractedKeywords == null
                ? Collections.emptyList()
                : new ArrayList<>(extractedKeywords);
        this.category = category;
        this.vibe = vibe;
        this.searchQueries = searchQueries == null
                ? Collections.emptyList()
                : new ArrayList<>(searchQueries);
        this.locationHint = locationHint;
        this.warnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
        this.failureCode = failureCode;
    }

    public List<AiPlaceSuggestion> getPlaces() {
        return new ArrayList<>(places);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public List<String> getExtractedKeywords() {
        return new ArrayList<>(extractedKeywords);
    }

    public String getCategory() {
        return category;
    }

    public String getVibe() {
        return vibe;
    }

    public List<String> getSearchQueries() {
        return new ArrayList<>(searchQueries);
    }

    public String getLocationHint() {
        return locationHint;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
