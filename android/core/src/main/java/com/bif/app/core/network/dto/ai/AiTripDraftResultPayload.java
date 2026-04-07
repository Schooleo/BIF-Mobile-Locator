package com.bif.app.core.network.dto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiTripDraftResultPayload {
    public final AiTripDraftPayload draft;
    public final List<AiSuggestedPlacePayload> candidatePlaces;
    public final List<String> warnings;
    public final String failureCode;

    public AiTripDraftResultPayload(AiTripDraftPayload draft,
                                    List<AiSuggestedPlacePayload> candidatePlaces,
                                    List<String> warnings,
                                    String failureCode) {
        this.draft = draft;
        this.candidatePlaces = candidatePlaces == null
                ? Collections.emptyList() : new ArrayList<>(candidatePlaces);
        this.warnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
        this.failureCode = failureCode;
    }
}
