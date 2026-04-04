package com.bif.server.features.ai.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VibeHintNormalizerTest {

    private final VibeHintNormalizer normalizer = new VibeHintNormalizer();

    @Test
    void expand_WhenSupported_ReturnsDeterministicHints() {
        List<String> hints = normalizer.expand("cozy");

        assertEquals(List.of("cozy", "quiet", "romantic"), hints);
    }

    @Test
    void expand_WhenUnsupported_ReturnsEmptyList() {
        List<String> hints = normalizer.expand("mysterious");

        assertEquals(List.of(), hints);
    }
}
