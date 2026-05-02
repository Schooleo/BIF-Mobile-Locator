package com.bif.app.core.utils;

import androidx.annotation.Nullable;

public final class InputLimits {

    public static final int USERNAME_MAX_LENGTH = 15;
    public static final int TRIP_TITLE_MAX_LENGTH = 50;

    private InputLimits() {
    }

    public static String trimAndLimit(@Nullable String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (maxLength <= 0 || trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }
}
