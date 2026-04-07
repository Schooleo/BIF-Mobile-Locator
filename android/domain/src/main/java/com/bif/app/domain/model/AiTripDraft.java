package com.bif.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiTripDraft {
    private final String title;
    private final String summary;
    private final List<AiTripDraftStop> stops;

    public AiTripDraft(String title, String summary, List<AiTripDraftStop> stops) {
        this.title = title;
        this.summary = summary;
        this.stops = stops == null ? Collections.emptyList() : new ArrayList<>(stops);
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public List<AiTripDraftStop> getStops() {
        return new ArrayList<>(stops);
    }
}
