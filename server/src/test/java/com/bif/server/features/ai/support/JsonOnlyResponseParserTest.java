package com.bif.server.features.ai.support;

import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.ai.exceptions.AiParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonOnlyResponseParserTest {

    private final JsonOnlyResponseParser parser =
            new JsonOnlyResponseParser(new ObjectMapper());

    @Test
    void parse_ParsesStrictJson() {
        PlaceSearchExtraction extraction = parser.parse(
                "{\"searchQueries\":[\"coffee near me\"],\"keywords\":[\"coffee\"],"
                        + "\"category\":\"cafe\",\"vibe\":\"quiet\"}",
                PlaceSearchExtraction.class
        );

        assertEquals("coffee near me", extraction.searchQueries().getFirst());
        assertEquals(1, extraction.keywords().size());
        assertEquals("coffee", extraction.keywords().getFirst());
        assertEquals("cafe", extraction.category());
        assertEquals("quiet", extraction.vibe());
    }

    @Test
    void parse_StripsMarkdownFence() {
        PlaceSearchExtraction extraction = parser.parse(
                """
                ```json
                {"searchQueries":["museum in district 1"],"keywords":["museum"],"category":"history","vibe":null}
                ```
                """,
                PlaceSearchExtraction.class
        );

        assertEquals("museum", extraction.keywords().getFirst());
        assertEquals("history", extraction.category());
    }

    @Test
    void parse_StripsLeadingFillerBeforeJson() {
        PlaceSearchExtraction extraction = parser.parse(
                "Here is the JSON you asked for:\n"
                        + "{\"searchQueries\":[\"casual brunch\"],\"keywords\":[\"brunch\"],"
                        + "\"category\":null,\"vibe\":\"casual\"}",
                PlaceSearchExtraction.class
        );

        assertEquals("brunch", extraction.keywords().getFirst());
        assertEquals("casual", extraction.vibe());
    }

    @Test
    void parse_RejectsTrailingProse() {
        assertThrows(
                AiParseException.class,
                () -> parser.parse(
                        "{\"searchQueries\":[\"tea\"],\"keywords\":[\"tea\"],"
                                + "\"category\":null,\"vibe\":null}"
                                + "\nThat should help.",
                        PlaceSearchExtraction.class
                )
        );
    }

    @Test
    void parse_RejectsMalformedJson() {
        assertThrows(
                AiParseException.class,
                () -> parser.parse(
                        "{\"searchQueries\":[\"tea\"],\"keywords\":[\"tea\"],\"category\":",
                        PlaceSearchExtraction.class
                )
        );
    }
}
