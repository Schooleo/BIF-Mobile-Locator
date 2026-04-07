package com.bif.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AiTripDraftResult {
    private final AiTripDraft draft;
    private final List<Place> candidatePlaces;
    private final List<String> warnings;
    private final String failureCode;

    public AiTripDraftResult(AiTripDraft draft,
                             List<Place> candidatePlaces,
                             List<String> warnings,
                             String failureCode) {
        this.draft = Objects.requireNonNull(draft, "draft must not be null");
        this.candidatePlaces = candidatePlaces == null
                ? Collections.emptyList() : new ArrayList<>(candidatePlaces);
        this.warnings = warnings == null ? Collections.emptyList() : new ArrayList<>(warnings);
        this.failureCode = failureCode;
    }

    public AiTripDraft getDraft() {
        return draft;
    }

    public List<Place> getCandidatePlaces() {
        return new ArrayList<>(candidatePlaces);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public String getFailureCode() {
        return failureCode;
    }
}
