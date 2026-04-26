package com.bif.app.feature.social;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class AiDraftScheduleResolverTest {

    @Test
    public void resolveStopTimes_multiDayEqualStartTime_keepsSameDay() {
        ZoneId zone = ZoneId.systemDefault();
        long tripStartAt = LocalDate.of(2026, 5, 1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
        long tripEndAt = LocalDate.of(2026, 5, 3)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();

        AiDraftScheduleResolver.ScheduleCursor cursor =
                AiDraftScheduleResolver.newCursor(tripStartAt, tripEndAt);

        AiDraftScheduleResolver.resolveStopTimes(
                "",
                "18:00",
                "20:00",
                120,
                cursor
        );
        AiDraftScheduleResolver.ScheduledTime second =
                AiDraftScheduleResolver.resolveStopTimes(
                        "",
                        "20:00",
                        "21:00",
                        60,
                        cursor
                );

        assertEquals(
                LocalDate.of(2026, 5, 1),
                Instant.ofEpochMilli(second.arrivalAt).atZone(zone).toLocalDate()
        );
    }

    @Test
    public void resolveStopTimes_singleDayEqualStartTime_keepsSameDay() {
        ZoneId zone = ZoneId.systemDefault();
        long tripStartAt = LocalDate.of(2026, 5, 1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();

        AiDraftScheduleResolver.ScheduleCursor cursor =
                AiDraftScheduleResolver.newCursor(tripStartAt, tripStartAt);

        AiDraftScheduleResolver.resolveStopTimes(
                "",
                "18:00",
                "20:00",
                120,
                cursor
        );
        AiDraftScheduleResolver.ScheduledTime second =
                AiDraftScheduleResolver.resolveStopTimes(
                        "",
                        "20:00",
                        "21:00",
                        60,
                        cursor
                );

        assertEquals(
                LocalDate.of(2026, 5, 1),
                Instant.ofEpochMilli(second.arrivalAt).atZone(zone).toLocalDate()
        );
    }
}
