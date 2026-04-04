package com.bif.server.features.ai.agents;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.ai.support.JsonOnlyResponseParser;
import com.bif.server.features.place.models.Place;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TripDraftingAgent {

    private static final String ITINERARY_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "title": { "type": "string" },
                    "summary": { "type": ["string", "null"] },
                    "stops": {
                        "type": "array",
                        "minItems": %d,
                        "maxItems": %d,
                        "items": {
                            "type": "object",
                            "properties": {
                                "placeId": { "type": "string" },
                                "durationMinutes": {
                                    "type": "integer",
                                    "minimum": %d,
                                    "maximum": %d
                                },
                                "note": { "type": ["string", "null"] }
                            },
                            "required": ["placeId", "durationMinutes", "note"],
                            "additionalProperties": false
                        }
                    }
                },
                "required": ["title", "summary", "stops"],
                "additionalProperties": false
            }
            """.formatted(
            AiGenerationConstraints.MIN_STOPS,
            AiGenerationConstraints.MAX_STOPS,
            AiGenerationConstraints.MIN_STOP_DURATION_MINUTES,
            AiGenerationConstraints.MAX_STOP_DURATION_MINUTES
    );

    private final OllamaJsonClient ollamaJsonClient;
    private final JsonOnlyResponseParser jsonOnlyResponseParser;
    private final ObjectMapper objectMapper;

    public TripDraftingAgent(
            OllamaJsonClient ollamaJsonClient,
            JsonOnlyResponseParser jsonOnlyResponseParser,
            ObjectMapper objectMapper) {
        this.ollamaJsonClient = ollamaJsonClient;
        this.jsonOnlyResponseParser = jsonOnlyResponseParser;
        this.objectMapper = objectMapper;
    }

    public GeneratedItinerary draft(String userQuery, List<Place> allowedPlaces) {
        return execute(userQuery, allowedPlaces, null);
    }

    public GeneratedItinerary retry(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason) {
        return execute(userQuery, allowedPlaces, failureReason);
    }

    private GeneratedItinerary execute(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason) {
        String response = ollamaJsonClient.generateJson(
                buildSystemPrompt(),
                buildUserPrompt(userQuery, allowedPlaces, failureReason),
                ITINERARY_SCHEMA);
        return jsonOnlyResponseParser.parse(response, GeneratedItinerary.class);
    }

    private String buildSystemPrompt() {
        return """
                You draft grounded trip itineraries from approved place context.
                Output strictly valid JSON only.
                Do not include markdown fences, explanations, or conversational filler.
                You must use only the exact placeId values provided in the context.
                                Return 1-8 stops.
                                Use durationMinutes between 15 and 360.
                                Do not repeat the same placeId more than once.
                Return exactly this schema:
                {
                  "title": "string",
                  "summary": "string|null",
                  "stops": [
                    {
                      "placeId": "string",
                      "durationMinutes": 60,
                      "note": "string|null"
                    }
                  ]
                }
                Ensure the stop sequence is logical and uses only allowed placeId values.
                """;
    }

    private String buildUserPrompt(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason) {
        StringBuilder builder = new StringBuilder();
        builder.append("Original request: ").append(userQuery).append('\n');
        builder.append("Allowed place context JSON: ")
                .append(serializeAllowedPlaces(allowedPlaces))
                .append('\n');
        if (failureReason != null && !failureReason.isBlank()) {
            builder.append("Previous response was invalid: ")
                    .append(failureReason)
                    .append('\n');
            builder.append("Correct the itinerary and return only valid JSON.\n");
        }
        builder.append("Draft the itinerary now.");
        return builder.toString();
    }

    private String serializeAllowedPlaces(List<Place> allowedPlaces) {
        List<AllowedPlace> context = allowedPlaces.stream()
                .map(place -> new AllowedPlace(
                        place.getId(),
                        place.getName(),
                        place.getAddress(),
                        place.getTags()))
                .toList();
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trip context", e);
        }
    }

    private record AllowedPlace(
            String placeId,
            String name,
            String address,
            List<String> tags) {
    }
}
