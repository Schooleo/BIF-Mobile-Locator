package com.bif.server.features.ai;

import java.time.LocalTime;
import java.util.List;

public final class AiGenerationConstraints {

    public static final int MIN_KEYWORDS = 1;
    public static final int MAX_KEYWORDS = 6;
    public static final int MIN_SEARCH_QUERIES = 1;
    public static final int MAX_SEARCH_QUERIES = 6;
    public static final int MIN_STOPS = 1;
    public static final int MAX_STOPS = 8;
    public static final int MIN_STOP_DURATION_MINUTES = 15;
    public static final int MAX_STOP_DURATION_MINUTES = 360;
    public static final int MAX_TOTAL_DURATION_MINUTES = 12 * 60;

    private final LocalTime preferredStartTime;
    private final LocalTime preferredEndTime;
    private final List<String> targetVibes;

    private AiGenerationConstraints(
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            List<String> targetVibes) {
        this.preferredStartTime = preferredStartTime;
        this.preferredEndTime = preferredEndTime;
        this.targetVibes = targetVibes == null ? List.of() : List.copyOf(targetVibes);
    }

    public static AiGenerationConstraints none() {
        return new AiGenerationConstraints(null, null, List.of());
    }

    public static AiGenerationConstraints of(
            LocalTime preferredStartTime,
            LocalTime preferredEndTime,
            List<String> targetVibes) {
        return new AiGenerationConstraints(preferredStartTime, preferredEndTime, targetVibes);
    }

    public LocalTime getPreferredStartTime() {
        return preferredStartTime;
    }

    public LocalTime getPreferredEndTime() {
        return preferredEndTime;
    }

    public List<String> getTargetVibes() {
        return targetVibes;
    }

    public boolean hasPreferredTimeWindow() {
        return preferredStartTime != null || preferredEndTime != null;
    }

    public boolean hasTargetVibes() {
        return !targetVibes.isEmpty();
    }

    public boolean isEmpty() {
        return !hasPreferredTimeWindow() && !hasTargetVibes();
    }
}
