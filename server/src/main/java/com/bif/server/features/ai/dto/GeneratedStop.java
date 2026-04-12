package com.bif.server.features.ai.dto;

public record GeneratedStop(
        String placeId,
        Integer durationMinutes,
        String note,
        String plannedDateTime) {

    public GeneratedStop {
        placeId = normalize(placeId);
        note = normalize(note);
        plannedDateTime = normalize(plannedDateTime);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
