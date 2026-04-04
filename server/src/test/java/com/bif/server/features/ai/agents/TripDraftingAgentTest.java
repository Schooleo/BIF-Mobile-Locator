package com.bif.server.features.ai.agents;

import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.ai.support.JsonOnlyResponseParser;
import com.bif.server.features.place.models.Place;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripDraftingAgentTest {

    @Test
    void draft_UsesAllowedPlaceContextAndParsesResponse() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString())).thenReturn(
                "{\"title\":\"Day Out\",\"summary\":\"A relaxed plan\","
                        + "\"stops\":[{\"placeId\":\"p1\",\"durationMinutes\":90,"
                        + "\"note\":\"Start here\"}]}"
        );

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
                new JsonOnlyResponseParser(new ObjectMapper()),
                new ObjectMapper()
        );

        Place place = new Place();
        place.setId("p1");
        place.setName("Notre Dame");
        place.setAddress("District 1");
        place.setTags(List.of("historic"));

        GeneratedItinerary itinerary = agent.draft("plan a relaxing day", List.of(place));

        assertEquals("Day Out", itinerary.title());
        assertEquals(1, itinerary.stops().size());
        assertEquals("p1", itinerary.stops().getFirst().placeId());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(systemCaptor.capture(), userCaptor.capture(), schemaCaptor.capture());
        JsonNode schema = readTree(schemaCaptor.getValue());
        assertTrue(systemCaptor.getValue().contains("exact placeId values"));
        assertTrue(userCaptor.getValue().contains("\"placeId\":\"p1\""));
        assertTrue(userCaptor.getValue().contains("plan a relaxing day"));
        assertEquals(1, schema.path("properties").path("stops").path("minItems").asInt());
        assertEquals(8, schema.path("properties").path("stops").path("maxItems").asInt());
        assertEquals(15, schema.path("properties").path("stops").path("items")
                .path("properties").path("durationMinutes").path("minimum").asInt());
        assertEquals(360, schema.path("properties").path("stops").path("items")
                .path("properties").path("durationMinutes").path("maximum").asInt());
    }

    @Test
    void retry_InjectsFailureReasonIntoPrompt() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString())).thenReturn(
                "{\"title\":\"Retry\",\"summary\":null,"
                        + "\"stops\":[{\"placeId\":\"p1\",\"durationMinutes\":60,"
                        + "\"note\":null}]}"
        );

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
                new JsonOnlyResponseParser(new ObjectMapper()),
                new ObjectMapper()
        );

        Place place = new Place();
        place.setId("p1");
        place.setName("Zoo");

        agent.retry("make a zoo plan", List.of(place), "Unknown placeId values");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(anyString(), userCaptor.capture(), anyString());
        assertTrue(userCaptor.getValue().contains("Previous response was invalid"));
        assertTrue(userCaptor.getValue().contains("Unknown placeId values"));
    }

    private JsonNode readTree(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (IOException e) {
            throw new AssertionError("Expected valid JSON schema", e);
        }
    }
}
