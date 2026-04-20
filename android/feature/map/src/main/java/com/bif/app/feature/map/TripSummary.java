package com.bif.app.feature.map;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Objects;

public final class TripSummary {
    private final Long startTime;
    private final Long endTime;

    public TripSummary(@NonNull Long startTime, @NonNull Long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    @NonNull
    public Long getStartTime() {
        return startTime;
    }

    @NonNull
    public Long getEndTime() {
        return endTime;
    }

    @NonNull
    public String getDurationFormatted() {
        long safeStart = Math.max(0L, startTime);
        long safeEnd = Math.max(0L, endTime);
        long durationSeconds = Math.max(0L, (safeEnd - safeStart) / 1000L);
        long minutes = durationSeconds / 60L;
        long seconds = durationSeconds % 60L;
        return String.format(Locale.getDefault(), "%d phút %d giây", minutes, seconds);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TripSummary)) {
            return false;
        }
        TripSummary that = (TripSummary) other;
        return Objects.equals(startTime, that.startTime)
                && Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startTime, endTime);
    }

    @NonNull
    @Override
    public String toString() {
        return "TripSummary(startTime=" + startTime + ", endTime=" + endTime + ")";
    }
}
