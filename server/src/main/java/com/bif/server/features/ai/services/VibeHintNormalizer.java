package com.bif.server.features.ai.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class VibeHintNormalizer {

    private static final Map<String, List<String>> VIBE_HINTS = Map.of(
            "cozy", List.of("quiet", "romantic"),
            "chill", List.of("relaxed", "casual"),
            "historic", List.of("landmark", "museum"),
            "nightlife", List.of("bar", "late-night"),
            "family-friendly", List.of("family", "kid-friendly"));

    public List<String> expand(String vibe) {
        String normalized = normalize(vibe);
        if (normalized == null) {
            return List.of();
        }

        List<String> mappedHints = VIBE_HINTS.get(normalized);
        if (mappedHints == null) {
            return List.of();
        }

        LinkedHashSet<String> hints = new LinkedHashSet<>();
        hints.add(normalized);
        hints.addAll(mappedHints);
        return List.copyOf(hints);
    }

    public String normalize(String vibe) {
        if (vibe == null) {
            return null;
        }

        String normalized = vibe.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace(' ', '-');
        return normalized.isBlank() ? null : normalized;
    }
}
