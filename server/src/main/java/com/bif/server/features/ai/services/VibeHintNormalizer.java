package com.bif.server.features.ai.services;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class VibeHintNormalizer {

    private static final Map<String, List<String>> VIBE_HINTS = Map.ofEntries(
            Map.entry("cozy", List.of("quiet", "romantic")),
            Map.entry("chill", List.of("relaxed", "casual", "RELAXING")),
            Map.entry("relaxing", List.of("relaxed", "quiet", "RELAXING")),
            Map.entry("yen-tinh", List.of("quiet", "relaxed", "RELAXING")),
            Map.entry("thu-gian", List.of("relaxed", "quiet", "RELAXING")),
            Map.entry("lang-man", List.of("romantic", "date", "COUPLE")),
            Map.entry("romantic", List.of("romantic", "date", "COUPLE")),
            Map.entry("couple", List.of("romantic", "date", "COUPLE")),
            Map.entry("soi-dong", List.of("nightlife", "bar", "NIGHTLIFE")),
            Map.entry("nao-nhiet", List.of("nightlife", "bar", "NIGHTLIFE")),
            Map.entry("nightlife", List.of("bar", "late-night", "NIGHTLIFE")),
            Map.entry("historic", List.of("landmark", "museum", "CULTURE")),
            Map.entry("lich-su", List.of("historic", "museum", "CULTURE")),
            Map.entry("van-hoa", List.of("museum", "landmark", "CULTURE")),
            Map.entry("family-friendly", List.of("family", "kid-friendly", "FAMILY")),
            Map.entry("gia-dinh", List.of("family", "kid-friendly", "FAMILY")),
            Map.entry("foodie", List.of("restaurant", "food", "FOOD")),
            Map.entry("am-thuc", List.of("restaurant", "food", "FOOD")),
            Map.entry("cafe", List.of("cafe", "coffee", "RELAXING")));

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

    public List<String> extractTargetVibes(String text) {
        String normalizedText = normalize(text);
        if (normalizedText == null) {
            return List.of();
        }

        LinkedHashSet<String> vibes = new LinkedHashSet<>();
        for (String keyword : VIBE_HINTS.keySet()) {
            if (containsKeyword(normalizedText, keyword)) {
                vibes.addAll(expand(keyword));
            }
        }
        return List.copyOf(vibes);
    }

    public String normalize(String vibe) {
        if (vibe == null) {
            return null;
        }

        String normalized = Normalizer.normalize(vibe.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('_', '-')
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private boolean containsKeyword(String normalizedText, String keyword) {
        if (normalizedText.equals(keyword)) {
            return true;
        }
        List<String> tokens = new ArrayList<>(List.of(normalizedText.split("-")));
        List<String> keywordTokens = List.of(keyword.split("-"));
        if (keywordTokens.size() == 1) {
            return tokens.contains(keyword);
        }
        return normalizedText.contains(keyword);
    }
}
