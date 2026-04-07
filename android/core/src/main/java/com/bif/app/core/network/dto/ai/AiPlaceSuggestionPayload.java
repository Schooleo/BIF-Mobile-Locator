package com.bif.app.core.network.dto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiPlaceSuggestionPayload {
    public final List<AiSuggestedPlacePayload> places;
    public final List<String> warnings;
    public final String failureCode;

    public AiPlaceSuggestionPayload(List<AiSuggestedPlacePayload> places,
                                    List<String> warnings,
                                    String failureCode) {
        this.places = places == null ? Collections.emptyList() : new ArrayList<>(places);
        this.warnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
        this.failureCode = failureCode;
    }
}
