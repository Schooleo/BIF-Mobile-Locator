package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TripSummaryTest {

    @Test
    public void getDurationParts_returnsMinutesAndSeconds() {
        TripSummary summary = new TripSummary(1000L, 126000L);

        assertEquals(Long.valueOf(2L), summary.getDurationMinutes());
        assertEquals(Long.valueOf(5L), summary.getDurationSeconds());
    }
}
