package com.bif.server.features.ai.agents;

import org.springframework.stereotype.Component;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;

@Component
public class PlaceSuggestionAgent {

    private static final String EXTRACTION_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "searchQueries": {
                        "type": "array",
                        "minItems": %d,
                        "maxItems": %d,
                        "items": {
                            "type": "string",
                            "minLength": 1
                        }
                    },
                    "keywords": {
                        "type": "array",
                        "minItems": %d,
                        "maxItems": %d,
                        "items": {
                            "type": "string",
                            "minLength": 1
                        }
                    },
                    "category": {
                        "type": ["string", "null"]
                    },
                    "vibe": {
                        "type": ["string", "null"]
                    },
                    "locationHint": {
                        "type": ["string", "null"]
                    }
                },
                "required": ["searchQueries", "keywords", "category", "vibe", "locationHint"],
                "additionalProperties": false
            }
            """.formatted(
            AiGenerationConstraints.MIN_SEARCH_QUERIES,
            AiGenerationConstraints.MAX_SEARCH_QUERIES,
            AiGenerationConstraints.MIN_KEYWORDS,
            AiGenerationConstraints.MAX_KEYWORDS
    );

    private final OllamaJsonClient ollamaJsonClient;

    public PlaceSuggestionAgent(OllamaJsonClient ollamaJsonClient) {
        this.ollamaJsonClient = ollamaJsonClient;
    }

    public PlaceSearchExtraction extract(String userQuery) {
        return execute(userQuery, null);
    }

    public PlaceSearchExtraction retry(String userQuery, String failureReason) {
        return execute(userQuery, failureReason);
    }

    private PlaceSearchExtraction execute(String userQuery, String failureReason) {
        return ollamaJsonClient.generateJson(
                buildSystemPrompt(),
                buildUserPrompt(userQuery, failureReason),
                EXTRACTION_SCHEMA,
                PlaceSearchExtraction.class);
    }

    private String buildSystemPrompt() {
        return """
                You are a place-search parameter extraction engine.
                Output strictly valid JSON only.
                Do not include markdown fences, explanations, or conversational filler.
                Return exactly this schema:
                {
                    "searchQueries": ["string"],
                    "keywords": ["string"],
                    "category": "string|null",
                    "vibe": "string|null",
                    "locationHint": "string|null"
                }
                searchQueries must be concise, high-signal place-search strings ordered by best first.
                Use concise keywords suitable for place search grounding.
                When the user specifies both a city and a narrower district/neighborhood, preserve that full hierarchy in the best searchQueries.
                Set locationHint to the strongest location focus from the user request, keeping parent-city context when it disambiguates the area.
                Do not drop explicit city/district constraints in favor of generic tourism wording.
                """;
    }

    private String buildUserPrompt(String userQuery, String failureReason) {
        StringBuilder builder = new StringBuilder();
        builder.append("User query: ").append(userQuery).append('\n');
        if (failureReason != null && !failureReason.isBlank()) {
            builder.append("Previous response was invalid: ")
                    .append(failureReason)
                    .append('\n');
            builder.append("Correct the response and return only valid JSON.\n");
        }
        builder.append("Extract the search parameters now.");
        return builder.toString();
    }
}
