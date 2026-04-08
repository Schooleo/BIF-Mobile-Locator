package com.bif.app.core.network.dto.ai;

public class AiTripDraftStopPayload {
    public final String placeId;
    public final AiSuggestedPlacePayload place;
    public final int durationMinutes;
    public final String note;

    public AiTripDraftStopPayload(String placeId,
                                  AiSuggestedPlacePayload place,
                                  int durationMinutes,
                                  String note) {
        this.placeId = placeId;
        this.place = place;
        this.durationMinutes = durationMinutes;
        this.note = note;
    }
}
