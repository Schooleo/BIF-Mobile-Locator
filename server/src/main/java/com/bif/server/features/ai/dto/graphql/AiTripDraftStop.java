package com.bif.server.features.ai.dto.graphql;

import com.bif.server.features.place.models.Place;

/**
 * GraphQL view of an AI-drafted stop grounded to a candidate {@link Place}.
 *
 * @param placeId canonical candidate place id emitted by the drafting agent
 * @param place resolved non-null place for {@code placeId}
 * @param durationMinutes normalized non-null duration fallback in minutes, retained for compatibility
 * @param startTime optional ISO-local HH:mm start bound for the stop
 * @param endTime optional ISO-local HH:mm end bound for the stop
 * @param duration AI-provided duration in minutes; prefer this when non-null, otherwise use {@code durationMinutes}
 * @param note optional AI note for the stop
 * @param plannedDateTime optional concrete ISO-8601 arrival instant/date-time for scheduling
 */
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
