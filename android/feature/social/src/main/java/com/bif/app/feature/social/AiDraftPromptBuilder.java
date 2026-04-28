package com.bif.app.feature.social;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

final class AiDraftPromptBuilder {

    private static final DateTimeFormatter AI_DRAFT_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private AiDraftPromptBuilder() {
    }

    static String buildDraftQueryWithDateRange(String rawQuery, long startAt, long endAt) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty() || startAt <= 0L || endAt < startAt) {
            return query;
        }
        return query
                + "\n\nTrip date range:"
                + "\n- Start date: " + formatAiDraftDate(startAt)
                + "\n- End date: " + formatAiDraftDate(endAt)
                + "\nSchedule each stop within this date range and return concrete plannedDateTime values when possible.";
    }

    static String formatAiDraftDate(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(AI_DRAFT_DATE_FORMATTER);
    }
}
