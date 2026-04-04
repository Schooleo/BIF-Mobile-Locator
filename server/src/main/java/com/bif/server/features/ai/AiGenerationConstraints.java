package com.bif.server.features.ai;

public final class AiGenerationConstraints {

    public static final int MIN_KEYWORDS = 1;
    public static final int MAX_KEYWORDS = 6;
    public static final int MIN_STOPS = 1;
    public static final int MAX_STOPS = 8;
    public static final int MIN_STOP_DURATION_MINUTES = 15;
    public static final int MAX_STOP_DURATION_MINUTES = 360;
    public static final int MAX_TOTAL_DURATION_MINUTES = 12 * 60;

    private AiGenerationConstraints() {
    }
}
