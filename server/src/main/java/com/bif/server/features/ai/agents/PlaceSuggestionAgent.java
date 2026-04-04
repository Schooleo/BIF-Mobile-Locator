package com.bif.server.features.ai.agents;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.clients.OllamaJsonClient;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.ai.support.JsonOnlyResponseParser;
import org.springframework.stereotype.Component;

@Component
public class PlaceSuggestionAgent {

    private static final String EXTRACTION_SCHEMA = """
            {
                "type": "object",
                "properties": {
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
                    }
                },
                "required": ["keywords", "category", "vibe"],
                "additionalProperties": false
            }
            """.formatted(
            AiGenerationConstraints.MIN_KEYWORDS,
            AiGenerationConstraints.MAX_KEYWORDS
    );

    private final OllamaJsonClient ollamaJsonClient;
    private final JsonOnlyResponseParser jsonOnlyResponseParser;

    public PlaceSuggestionAgent(
            OllamaJsonClient ollamaJsonClient,
            JsonOnlyResponseParser jsonOnlyResponseParser) {
        this.ollamaJsonClient = ollamaJsonClient;
        this.jsonOnlyResponseParser = jsonOnlyResponseParser;
    }

    public PlaceSearchExtraction extract(String userQuery) {
        return execute(userQuery, null);
    }

    public PlaceSearchExtraction retry(String userQuery, String failureReason) {
        return execute(userQuery, failureReason);
    }

    private PlaceSearchExtraction execute(String userQuery, String failureReason) {
        String response = ollamaJsonClient.generateJson(
                buildSystemPrompt(),
                buildUserPrompt(userQuery, failureReason),
                EXTRACTION_SCHEMA);
        return jsonOnlyResponseParser.parse(response, PlaceSearchExtraction.class);
    }

    private String buildSystemPrompt() {
        return """
                You are a place-search parameter extraction engine.
                Output strictly valid JSON only.
                Do not include markdown fences, explanations, or conversational filler.
                Return exactly this schema:
                {
                  "keywords": ["string"],
                  "category": "string|null",
                  "vibe": "string|null"
                }
                Use concise keywords suitable for place search grounding.
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
