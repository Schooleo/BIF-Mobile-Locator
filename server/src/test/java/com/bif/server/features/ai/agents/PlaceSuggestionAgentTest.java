package com.bif.server.features.ai.agents;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class PlaceSuggestionAgentTest {

    @Test
    void extract_BuildsStrictPromptAndParsesResponse() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        PlaceSearchExtraction expectedExtraction = new PlaceSearchExtraction(
            java.util.List.of("coffee in district 1", "quiet cafe district 1"),
            java.util.List.of("coffee", "district 1"),
            "cafe",
            "quiet",
            "district 1"
        );
        when(client.generateJson(anyString(), anyString(), anyString(), eq(PlaceSearchExtraction.class)))
            .thenReturn(expectedExtraction);

        PlaceSuggestionAgent agent = new PlaceSuggestionAgent(client);

        PlaceSearchExtraction extraction = agent.extract("quiet coffee in district 1");

        assertEquals(2, extraction.searchQueries().size());
        assertEquals("coffee in district 1", extraction.searchQueries().getFirst());
        assertEquals(2, extraction.keywords().size());
        assertEquals("cafe", extraction.category());
        assertEquals("quiet", extraction.vibe());
        assertEquals("district 1", extraction.locationHint());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(
            systemCaptor.capture(),
            userCaptor.capture(),
            schemaCaptor.capture(),
            eq(PlaceSearchExtraction.class));
        JsonNode schema = readTree(schemaCaptor.getValue());
        assertTrue(systemCaptor.getValue().contains("Output strictly valid JSON only"));
        assertTrue(systemCaptor.getValue().contains("searchQueries"));
        assertTrue(systemCaptor.getValue().contains("\"keywords\""));
        assertTrue(systemCaptor.getValue().contains("locationHint"));
        assertTrue(systemCaptor.getValue().contains("full hierarchy"));
        assertTrue(userCaptor.getValue().contains("quiet coffee in district 1"));
        assertEquals(1, schema.path("properties").path("searchQueries").path("minItems").asInt());
        assertEquals(6, schema.path("properties").path("searchQueries").path("maxItems").asInt());
        assertEquals(1, schema.path("properties").path("keywords").path("minItems").asInt());
        assertEquals(6, schema.path("properties").path("keywords").path("maxItems").asInt());
        assertEquals(1, schema.path("properties").path("keywords").path("items").path("minLength").asInt());
        assertTrue(!schema.path("additionalProperties").asBoolean(true));
    }

    @Test
    void retry_InjectsFailureReasonIntoPrompt() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), eq(PlaceSearchExtraction.class)))
            .thenReturn(new PlaceSearchExtraction(
                java.util.List.of("history museum"),
                java.util.List.of("museum"),
                "history",
                null,
                null
            ));

        PlaceSuggestionAgent agent = new PlaceSuggestionAgent(client);

        agent.retry("history museum", "AI response contained trailing content");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(
            anyString(),
            userCaptor.capture(),
            schemaCaptor.capture(),
            eq(PlaceSearchExtraction.class));
        JsonNode schema = readTree(schemaCaptor.getValue());
        assertTrue(userCaptor.getValue().contains("Previous response was invalid"));
        assertTrue(userCaptor.getValue().contains("trailing content"));
        assertEquals(6, schema.path("properties").path("keywords").path("maxItems").asInt());
    }

    private JsonNode readTree(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (IOException e) {
            throw new AssertionError("Expected valid JSON schema", e);
        }
    }
}
