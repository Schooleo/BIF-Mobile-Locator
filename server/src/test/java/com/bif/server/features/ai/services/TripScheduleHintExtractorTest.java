package com.bif.server.features.ai.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripScheduleHintExtractorTest {

    @Test
    void extract_ParsesVietnameseDayAndWeekSignals() {
        TripScheduleHintExtractor extractor = new TripScheduleHintExtractor(
                Clock.fixed(
                        Instant.parse("2026-04-11T00:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        TripScheduleHintExtractor.TripScheduleHints hints
                = extractor.extract("Chuy\u1ebfn \u0111i 1 ng\u00e0y sau 1 tu\u1ea7n");

        assertTrue(hints.shouldArrangeDateTime());
        assertEquals(1, hints.daySpan());
        assertEquals(7, hints.startOffsetDays());
        assertNotNull(hints.suggestedStartDateTime());
        assertEquals(18, hints.suggestedStartDateTime().getDayOfMonth());
        assertEquals(9, hints.suggestedStartDateTime().getHour());
        assertTrue(hints.promptDirective().contains("coherent plannedDateTime"));
    }

    @Test
    void extract_ReturnsNoneForNoTimingIntent() {
        TripScheduleHintExtractor extractor = new TripScheduleHintExtractor(
                Clock.fixed(
                        Instant.parse("2026-04-11T00:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        TripScheduleHintExtractor.TripScheduleHints hints
                = extractor.extract("qu\u00e1n cafe y\u00ean t\u0129nh \u1edf h\u00e0 n\u1ed9i");

        assertFalse(hints.shouldArrangeDateTime());
        assertEquals(0, hints.daySpan());
        assertEquals(0, hints.startOffsetDays());
        assertNull(hints.suggestedStartDateTime());
        assertNull(hints.promptDirective());
    }

    @Test
    void extract_ParsesEnglishTimingSignals() {
        TripScheduleHintExtractor extractor = new TripScheduleHintExtractor(
                Clock.fixed(
                        Instant.parse("2026-04-11T00:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));

        TripScheduleHintExtractor.TripScheduleHints hints = extractor.extract(
                "Plan a 2 day food trip in Hanoi starting next week in the evening");

        assertTrue(hints.shouldArrangeDateTime());
        assertEquals(2, hints.daySpan());
        assertEquals(7, hints.startOffsetDays());
        assertNotNull(hints.suggestedStartDateTime());
        assertEquals(18, hints.suggestedStartDateTime().getHour());
    }
}
