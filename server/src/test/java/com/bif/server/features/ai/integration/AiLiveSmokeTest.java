package com.bif.server.features.ai.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_LIVE_SMOKE_ENABLED", matches = "true")
class AiLiveSmokeTest {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void aiMutations_WorkAgainstLiveServer() throws Exception {
        String baseUrl = env("AI_LIVE_SMOKE_BASE_URL", "http://localhost:8080");
        String password = env("AI_LIVE_SMOKE_PASSWORD", "Password123!");
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String username = "ai-smoke-" + unique;
        String email = "ai-smoke-" + unique + "@bif.local";

        JsonNode unauthorized = graphQl(
                baseUrl,
                null,
                """
                mutation Suggest($query: String!) {
                  suggestPlacesFromQuery(query: $query) {
                    failureCode
                    warnings
                  }
                }
                """,
                "{\"query\":\"historic place district 1\"}"
        );
        assertTrue(unauthorized.path("errors").isMissingNode()
                || unauthorized.path("errors").isEmpty());
        assertEquals(
                "UNAUTHORIZED",
                unauthorized.path("data")
                        .path("suggestPlacesFromQuery")
                        .path("failureCode")
                        .asText()
        );

        String token = register(baseUrl, username, email, password);
        assertNotNull(token);

        JsonNode suggested = awaitGraphQl(
                baseUrl,
                token,
                """
                mutation Suggest($query: String!) {
                  suggestPlacesFromQuery(query: $query) {
                    failureCode
                    warnings
                    places {
                      id
                      name
                    }
                  }
                }
                """,
                "{\"query\":\"historic church landmark district 1 saigon\"}",
                payload -> {
                    JsonNode suggestion = payload.path("data").path("suggestPlacesFromQuery");
                    return suggestion.path("failureCode").isNull()
                            && suggestion.path("places").isArray()
                            && !suggestion.path("places").isEmpty();
                }
        );
        JsonNode suggestionPayload = suggested.path("data").path("suggestPlacesFromQuery");
        assertTrue(suggestionPayload.path("failureCode").isNull());
        assertFalse(suggestionPayload.path("places").isEmpty());

        JsonNode drafted = awaitGraphQl(
                baseUrl,
                token,
                """
                mutation Draft($query: String!) {
                  draftTripFromQuery(query: $query) {
                    failureCode
                    warnings
                    candidatePlaces {
                      id
                    }
                    draft {
                      title
                      stops {
                        placeId
                        durationMinutes
                      }
                    }
                  }
                }
                """,
                "{\"query\":\"plan a historic district 1 outing\"}",
                payload -> {
                    JsonNode draft = payload.path("data").path("draftTripFromQuery");
                    return draft.path("failureCode").isNull()
                            && draft.path("candidatePlaces").isArray()
                            && !draft.path("candidatePlaces").isEmpty()
                            && !draft.path("draft").path("stops").isEmpty();
                }
        );
        JsonNode draftPayload = drafted.path("data").path("draftTripFromQuery");
        assertTrue(draftPayload.path("failureCode").isNull());
        assertFalse(draftPayload.path("candidatePlaces").isEmpty());
        assertFalse(draftPayload.path("draft").path("stops").isEmpty());
    }

    private String register(String baseUrl, String username, String email, String password)
            throws IOException, InterruptedException {
        String requestBody = """
                {
                  "username":"%s",
                  "email":"%s",
                  "password":"%s",
                  "confirmPassword":"%s"
                }
                """.formatted(username, email, password, password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(201, response.statusCode());
        JsonNode payload = objectMapper.readTree(response.body());
        return payload.path("accessToken").asText(null);
    }

    private JsonNode graphQl(
            String baseUrl,
            String accessToken,
            String query,
            String variablesJson) throws IOException, InterruptedException {
        String requestBody = """
                {
                  "query": %s,
                  "variables": %s
                }
                """.formatted(
                objectMapper.writeValueAsString(query),
                variablesJson
        );
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/graphql"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));
        if (accessToken != null) {
            requestBuilder.header("Authorization", "Bearer " + accessToken);
        }
        HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertEquals(200, response.statusCode());
        JsonNode payload = objectMapper.readTree(response.body());
        assertTrue(payload.path("errors").isMissingNode()
                || payload.path("errors").isEmpty());
        return payload;
    }

    private JsonNode awaitGraphQl(
            String baseUrl,
            String accessToken,
            String query,
            String variablesJson,
            ThrowingPredicate<JsonNode> predicate) throws Exception {
        JsonNode lastPayload = null;
        for (int attempt = 0; attempt < 12; attempt++) {
            lastPayload = graphQl(baseUrl, accessToken, query, variablesJson);
            if (predicate.test(lastPayload)) {
                return lastPayload;
            }
            Thread.sleep(5000);
        }
        throw new AssertionError("AI live smoke did not become ready in time. Last payload: "
                + lastPayload);
    }

    private String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    private interface ThrowingPredicate<T> {
        boolean test(T value) throws Exception;
    }
}
