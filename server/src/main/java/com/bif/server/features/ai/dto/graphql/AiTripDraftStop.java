package com.bif.server.features.ai.dto.graphql;

import com.bif.server.features.place.models.Place;

public record AiTripDraftStop(
        String placeId,
        Place place,
        int durationMinutes,
        String startTime,
        String endTime,
        Integer duration,
        String note,
        String plannedDateTime) {
}
