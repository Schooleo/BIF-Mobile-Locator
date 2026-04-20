package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TripSummaryTest {

    @Test
    public void getDurationFormatted_formatsAsMinutesAndSeconds() {
        TripSummary summary = new TripSummary(1000L, 126000L);

        assertEquals("2 phút 5 giây", summary.getDurationFormatted());
    }
}
