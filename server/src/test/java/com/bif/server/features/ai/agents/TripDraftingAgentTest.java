package com.bif.server.features.ai.agents;

import java.io.IOException;
import java.util.List;

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
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.ai.dto.GeneratedStop;
import com.bif.server.features.place.models.Place;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class TripDraftingAgentTest {

    @Test
    void draft_UsesAllowedPlaceContextAndParsesResponse() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), eq(GeneratedItinerary.class)))
                .thenReturn(new GeneratedItinerary(
                        "Day Out",
                        "A relaxed plan",
                        List.of(new GeneratedStop("p1", 90, "Start here", "2026-01-01T09:00:00Z"))
                ));

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
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
        assertEquals("2026-01-01T09:00:00Z", itinerary.stops().getFirst().plannedDateTime());

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> schemaCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).generateJson(
                systemCaptor.capture(),
                userCaptor.capture(),
                schemaCaptor.capture(),
                eq(GeneratedItinerary.class));
        JsonNode schema = readTree(schemaCaptor.getValue());
        assertTrue(systemCaptor.getValue().contains("exact placeId values"));
        assertTrue(systemCaptor.getValue().contains("keep most stops within that focus"));
        assertTrue(userCaptor.getValue().contains("\"placeId\":\"p1\""));
        assertTrue(userCaptor.getValue().contains("plan a relaxing day"));
        assertEquals(1, schema.path("properties").path("stops").path("minItems").asInt());
        assertEquals(8, schema.path("properties").path("stops").path("maxItems").asInt());
        assertEquals(15, schema.path("properties").path("stops").path("items")
                .path("properties").path("durationMinutes").path("minimum").asInt());
        assertEquals(360, schema.path("properties").path("stops").path("items")
                .path("properties").path("durationMinutes").path("maximum").asInt());
        assertTrue(schema.path("properties").path("stops").path("items")
                .path("properties").has("plannedDateTime"));
        assertTrue(systemCaptor.getValue().contains("Every stop must include a fitting note"));
    }

    @Test
    void draft_InjectsSchedulingHintsWhenProvided() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), eq(GeneratedItinerary.class)))
                .thenReturn(new GeneratedItinerary(
                        "Day Out",
                        null,
                        List.of(new GeneratedStop("p1", 60, "Start here", "2026-04-18T09:00:00+07:00"))
                ));

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
                new ObjectMapper()
        );

        Place place = new Place();
        place.setId("p1");
        place.setName("Cafe");

        agent.draft(
                "Chuy\u1ebfn \u0111i 1 ng\u00e0y sau 1 tu\u1ea7n",
                List.of(place),
                "Preferred trip length: 1 day. Suggested first stop datetime: 2026-04-18T09:00:00+07:00"
        );

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
                verify(client).generateJson(anyString(), userCaptor.capture(), anyString(), eq(GeneratedItinerary.class));
        assertTrue(userCaptor.getValue().contains("Scheduling hints:"));
        assertTrue(userCaptor.getValue().contains("Suggested first stop datetime"));
    }

    @Test
    void retry_InjectsFailureReasonIntoPrompt() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), eq(GeneratedItinerary.class)))
                .thenReturn(new GeneratedItinerary(
                        "Retry",
                        null,
                        List.of(new GeneratedStop("p1", 60, null, null))
                ));

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
                new ObjectMapper()
        );

        Place place = new Place();
        place.setId("p1");
        place.setName("Zoo");

        agent.retry("make a zoo plan", List.of(place), "Unknown placeId values");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
                verify(client).generateJson(anyString(), userCaptor.capture(), anyString(), eq(GeneratedItinerary.class));
        assertTrue(userCaptor.getValue().contains("Previous response was invalid"));
        assertTrue(userCaptor.getValue().contains("Unknown placeId values"));
    }

    @Test
    void draft_AllowsMissingOptionalStopFields() {
        OllamaJsonClient client = mock(OllamaJsonClient.class);
        when(client.generateJson(anyString(), anyString(), anyString(), eq(GeneratedItinerary.class)))
                .thenReturn(new GeneratedItinerary(
                        "Basic Draft",
                        null,
                        List.of(new GeneratedStop("p1", 60, null, null))
                ));

        TripDraftingAgent agent = new TripDraftingAgent(
                client,
                new ObjectMapper()
        );

        Place place = new Place();
        place.setId("p1");
        place.setName("Cafe");

        GeneratedItinerary itinerary = agent.draft("simple district request", List.of(place));

        assertEquals("Basic Draft", itinerary.title());
        assertEquals(1, itinerary.stops().size());
        assertEquals("p1", itinerary.stops().getFirst().placeId());
    }

    private JsonNode readTree(String value) {
        try {
            return new ObjectMapper().readTree(value);
        } catch (IOException e) {
            throw new AssertionError("Expected valid JSON schema", e);
        }
    }
}
