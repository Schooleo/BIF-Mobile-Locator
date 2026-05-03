package com.bif.server.features.ai.agents;

import java.util.List;

import org.springframework.stereotype.Component;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.place.models.Place;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                                "startTime": { "type": ["string", "null"] },
                                "endTime": { "type": ["string", "null"] },
                                "duration": {
                                    "type": "integer",
                                    "minimum": %d,
                                    "maximum": %d
                                },
                                "note": { "type": ["string", "null"] },
                                "plannedDateTime": { "type": ["string", "null"] }
                            },
                            "required": ["placeId", "durationMinutes", "startTime", "endTime", "duration"],
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
            AiGenerationConstraints.MAX_STOP_DURATION_MINUTES,
            AiGenerationConstraints.MIN_STOP_DURATION_MINUTES,
            AiGenerationConstraints.MAX_STOP_DURATION_MINUTES
    );

    private final OllamaJsonClient ollamaJsonClient;
    private final ObjectMapper objectMapper;

    public TripDraftingAgent(
            OllamaJsonClient ollamaJsonClient,
            ObjectMapper objectMapper) {
        this.ollamaJsonClient = ollamaJsonClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedItinerary draft(String userQuery, List<Place> allowedPlaces) {
        return draft(userQuery, allowedPlaces, null);
    }

    public GeneratedItinerary draft(
            String userQuery,
            List<Place> allowedPlaces,
            String schedulingHint) {
        return execute(userQuery, allowedPlaces, null, schedulingHint);
    }

    public GeneratedItinerary retry(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason) {
        return retry(userQuery, allowedPlaces, failureReason, null);
    }

    public GeneratedItinerary retry(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason,
            String schedulingHint) {
        return execute(userQuery, allowedPlaces, failureReason, schedulingHint);
    }

    private GeneratedItinerary execute(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason,
            String schedulingHint) {
        return ollamaJsonClient.generateJson(
                buildSystemPrompt(),
                buildUserPrompt(userQuery, allowedPlaces, failureReason, schedulingHint),
                ITINERARY_SCHEMA,
                GeneratedItinerary.class);
    }

    private String buildSystemPrompt() {
        return """
                You draft grounded trip itineraries from approved place context.
                Output strictly valid JSON only.
                Do not include markdown fences, explanations, or conversational filler.
                You must use only the exact placeId values provided in the context.
                Return 1-8 stops.
                Use durationMinutes between 15 and 360.
                For each stop in the itinerary, you MUST provide 'startTime' (String, format HH:mm), 'endTime' (String, format HH:mm), and 'duration' (Integer, total minutes spent). Include these fields in your JSON output explicitly.
                Treat durationMinutes as the canonical scheduled duration in minutes; duration is a compatibility alias and MUST equal durationMinutes.
                Use 24-hour HH:mm values for startTime and endTime and make endTime exactly startTime plus duration minutes when possible.
                Do not repeat the same placeId more than once.
                Return exactly this schema:
                {
                    "title": "string",
                    "summary": "string|null",
                    "stops": [
                        {
                            "placeId": "string",
                            "durationMinutes": 60,
                            "startTime": "HH:mm",
                            "endTime": "HH:mm",
                            "duration": 60,
                            "note": "string|null",
                            "plannedDateTime": "ISO-8601 string|null"
                        }
                    ]
                }
                Include plannedDateTime when the user asks for a schedule or explicit timing.
                Use null for plannedDateTime when timing is unknown.
                Every stop must include a fitting note tied to the place and trip intent.
                Keep note concise (1 sentence), specific, and useful (what to do, eat, see, or why this stop fits).
                Avoid generic notes like "Visit this place" or duplicated note text across stops.
                If the user specifies a city, district, neighborhood, or says the plan should be centered around an area, keep most stops within that focus whenever the allowed place addresses support it.
                Do not drift to a different city or district just because a place is more famous.
                Ensure the stop sequence is logical and uses only allowed placeId values.
                """;
    }

    private String buildUserPrompt(
            String userQuery,
            List<Place> allowedPlaces,
            String failureReason,
            String schedulingHint) {
        StringBuilder builder = new StringBuilder();
        builder.append("Original request: ").append(userQuery).append('\n');
        builder.append("Allowed place context JSON: ")
                .append(serializeAllowedPlaces(allowedPlaces))
                .append('\n');
        if (schedulingHint != null && !schedulingHint.isBlank()) {
            builder.append("Scheduling hints: ")
                    .append(schedulingHint)
                    .append('\n');
        }
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
