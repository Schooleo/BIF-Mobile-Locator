package com.bif.app.core.network.dto.ai;

public class AiTripDraftStopPayload {
    public final String placeId;
    public final AiSuggestedPlacePayload place;
    public final int durationMinutes;
    public final String startTime;
    public final String endTime;
    public final Integer duration;
    public final String note;
    public final String plannedDateTime;

    public AiTripDraftStopPayload(String placeId,
                                  AiSuggestedPlacePayload place,
                                  int durationMinutes,
                                  String startTime,
                                  String endTime,
                                  Integer duration,
                                  String note,
                                  String plannedDateTime) {
        this.placeId = placeId;
        this.place = place;
        this.durationMinutes = durationMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.note = note;
        this.plannedDateTime = plannedDateTime;
    }
}
