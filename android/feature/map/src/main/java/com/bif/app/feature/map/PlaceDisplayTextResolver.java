package com.bif.app.feature.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

final class PlaceDisplayTextResolver {
    static final String FALLBACK_TITLE = "Unnamed place";
    static final String FALLBACK_ADDRESS = "Address unavailable";

    private PlaceDisplayTextResolver() {
    }

    @NonNull
    static String resolveTitle(@Nullable String preferredName,
            @Nullable String reverseGeocodeName,
            @Nullable String reverseGeocodeAddress) {
        if (hasMeaningfulTitle(preferredName)) {
            return preferredName.trim();
        }

        if (hasMeaningfulTitle(reverseGeocodeName)
                && !isSameAsAddress(reverseGeocodeName, reverseGeocodeAddress)) {
            return reverseGeocodeName.trim();
        }

        return FALLBACK_TITLE;
    }

    @NonNull
    static String resolveAddress(@Nullable String placeName,
            @Nullable String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return FALLBACK_ADDRESS;
        }

        String normalized = rawAddress.trim();
        if (placeName == null || placeName.trim().isEmpty()) {
            return normalized;
        }

        String name = placeName.trim();
        if (normalized.equalsIgnoreCase(name)) {
            return FALLBACK_ADDRESS;
        }

        if (normalized.regionMatches(true, 0, name, 0, name.length())) {
            String suffix = normalized.substring(name.length()).trim();
            while (!suffix.isEmpty()) {
                char first = suffix.charAt(0);
                if (first == ',' || first == '-' || first == ':' || first == ' ') {
                    suffix = suffix.substring(1).trim();
                    continue;
                }
                break;
            }
            if (!suffix.isEmpty()) {
                normalized = suffix;
            }
        }

        int commaIndex = normalized.indexOf(',');
        if (commaIndex > 0) {
            String firstSegment = normalized.substring(0, commaIndex).trim();
            if (firstSegment.equalsIgnoreCase(name)) {
                String tail = normalized.substring(commaIndex + 1).trim();
                if (!tail.isEmpty()) {
                    normalized = tail;
                }
            }
        }

        return normalized.isEmpty() ? FALLBACK_ADDRESS : normalized;
    }

    static boolean hasMeaningfulTitle(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !normalized.equals("selected location")
                && !normalized.equals(FALLBACK_TITLE.toLowerCase(Locale.ROOT))
                && !normalized.equals(FALLBACK_ADDRESS.toLowerCase(Locale.ROOT))
                && !normalized.equals("unknown address");
    }

    private static boolean isSameAsAddress(@Nullable String candidate,
            @Nullable String addressText) {
        if (candidate == null || addressText == null) {
            return false;
        }

        String normalizedCandidate = candidate.trim();
        String normalizedAddress = addressText.trim();
        return !normalizedCandidate.isEmpty()
                && !normalizedAddress.isEmpty()
                && normalizedCandidate.equalsIgnoreCase(normalizedAddress);
    }

}