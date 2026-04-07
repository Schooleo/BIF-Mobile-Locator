package com.bif.app.core.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DateTimeUtils {

    /**
     * Get a human-readable relative timestamp in English regardless of device locale.
     * 
     * @param time The timestamp in milliseconds.
     * @return Formatted string like "Just now", "5m ago", "2h ago", "3d ago", or "Nov 15, 2023".
     */
    public static String getRelativeTimeEnglish(long time) {
        long now = System.currentTimeMillis();
        long diff = now - time;

        // Less than 1 minute
        if (diff < 60000) {
            return "Just now";
        }

        // Less than 1 hour
        if (diff < 3600000) {
            return (diff / 60000) + "m ago";
        }

        // Less than 24 hours
        if (diff < 86400000) {
            return (diff / 3600000) + "h ago";
        }

        // Less than 1 week
        if (diff < 604800000) {
            return (diff / 86400000) + "d ago";
        }

        // Default to date format
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);
        return sdf.format(new Date(time));
    }
}
