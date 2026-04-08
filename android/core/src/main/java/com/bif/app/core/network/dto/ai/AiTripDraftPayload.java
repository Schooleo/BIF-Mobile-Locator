package com.bif.app.core.network.dto.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiTripDraftPayload {
    public final String title;
    public final String summary;
    public final List<AiTripDraftStopPayload> stops;

    public AiTripDraftPayload(String title,
                              String summary,
                              List<AiTripDraftStopPayload> stops) {
        this.title = title;
        this.summary = summary;
        this.stops = stops == null ? Collections.emptyList() : new ArrayList<>(stops);
    }
}
