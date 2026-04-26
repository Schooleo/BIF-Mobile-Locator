package com.bif.server.features.ai.dto;

public record GeneratedStop(
        String placeId,
        Integer durationMinutes,
        String startTime,
        String endTime,
        Integer duration,
        String note,
        String plannedDateTime) {

    public GeneratedStop(String placeId, Integer durationMinutes, String note, String plannedDateTime) {
        this(placeId, durationMinutes, null, null, durationMinutes, note, plannedDateTime);
    }

    public GeneratedStop {
        placeId = normalize(placeId);
        startTime = normalize(startTime);
        endTime = normalize(endTime);
        note = normalize(note);
        plannedDateTime = normalize(plannedDateTime);
        if (duration == null) {
            duration = durationMinutes;
        }
        if (durationMinutes == null) {
            durationMinutes = duration;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
