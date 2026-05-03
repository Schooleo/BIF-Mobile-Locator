package com.bif.app.core.network.dto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiPlaceSuggestionPayload {
    public final List<AiSuggestedPlacePayload> places;
    public final List<String> extractedKeywords;
    public final String category;
    public final String vibe;
    public final List<String> searchQueries;
    public final String locationHint;
    public final List<String> warnings;
    public final String failureCode;

    public AiPlaceSuggestionPayload(List<AiSuggestedPlacePayload> places,
                                    List<String> warnings,
                                    String failureCode) {
        this(places, null, null, null, null, null, warnings, failureCode);
    }

    public AiPlaceSuggestionPayload(List<AiSuggestedPlacePayload> places,
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
}
