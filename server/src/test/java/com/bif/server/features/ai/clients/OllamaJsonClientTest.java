package com.bif.server.features.ai.clients;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.bif.server.features.ai.config.OllamaProperties;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.ai.exceptions.AiParseException;
import com.bif.server.features.ai.exceptions.AiUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OllamaJsonClientTest {

    @Test
    void buildRequestBody_UsesJsonModeAndConfiguredModel() {
        OllamaProperties properties = new OllamaProperties();
        properties.setModel("llama3.1");
        properties.setTemperature(0.2);
        ObjectMapper objectMapper = new ObjectMapper();

        OllamaJsonClient client = new OllamaJsonClient(
                Mockito.mock(HttpClient.class),
                objectMapper,
                properties
        );

        String body = client.buildRequestBody("system prompt", "user prompt");
        JsonNode json = readTree(objectMapper, body);

        assertEquals("llama3.1", json.path("model").asText());
        assertEquals("json", json.path("format").asText());
        assertEquals("system prompt", json.path("system").asText());
        assertEquals("user prompt", json.path("prompt").asText());
        assertEquals(0.2, json.path("options").path("temperature").asDouble());
    }

    @Test
    void buildRequestBody_WithSchema_EmbedsSchemaObject() {
        OllamaProperties properties = new OllamaProperties();
        properties.setModel("llama3.1");
        ObjectMapper objectMapper = new ObjectMapper();

        OllamaJsonClient client = new OllamaJsonClient(
                Mockito.mock(HttpClient.class),
                objectMapper,
                properties
        );

        String body = client.buildRequestBody(
                "system prompt",
                "user prompt",
                """
                {
                  "type":"object",
                  "properties":{"title":{"type":"string"}},
                  "required":["title"],
                  "additionalProperties":false
                }
                """
        );
        JsonNode json = readTree(objectMapper, body);

        assertEquals("object", json.path("format").path("type").asText());
        assertEquals("string", json.path("format").path("properties").path("title").path("type").asText());
        assertTrue(json.path("format").path("required").isArray());
        assertTrue(!json.path("format").path("additionalProperties").asBoolean(true));
    }

    @Test
    void generateJson_ThrowsOnNon2xxResponse() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        AiUpstreamException exception = assertThrows(
            AiUpstreamException.class,
            () -> client.generateJson("sys", "user")
        );
        assertTrue(exception.getMessage().contains("status 503"));
    }

    @Test
    void generateJson_ThrowsOnMalformedTransportPayload() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not-json");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        AiParseException exception = assertThrows(
            AiParseException.class,
            () -> client.generateJson("sys", "user")
        );
        assertTrue(exception.getMessage().contains("Failed to decode Ollama transport payload"));
    }

    @Test
    void generateJson_ThrowsWhenResponseFieldIsBlank() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"response\":\"\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        AiUpstreamException exception = assertThrows(
            AiUpstreamException.class,
            () -> client.generateJson("sys", "user")
        );
        assertTrue(exception.getMessage().contains("empty response payload"));
    }

    @Test
    void generateJson_ThrowsWhenTransportCarriesErrorPayload() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"error\":\"model overloaded\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        AiUpstreamException exception = assertThrows(
                AiUpstreamException.class,
                () -> client.generateJson("sys", "user")
        );
        assertTrue(exception.getMessage().contains("model overloaded"));
    }

    @Test
    void generateJson_IgnoresExtraFieldsWhenResponseIsPresent() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(
                "{\"response\":\"{\\\"ok\\\":true}\",\"done\":true,\"eval_count\":42}"
        );
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        assertEquals("{\"ok\":true}", client.generateJson("sys", "user"));
    }

    @Test
    void generateJson_ThrowsOnIoFailure() throws Exception {
        OllamaProperties properties = new OllamaProperties();
        HttpClient httpClient = Mockito.mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection failed"));

        OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

        AiUpstreamException exception = assertThrows(
            AiUpstreamException.class,
            () -> client.generateJson("sys", "user")
        );
        assertTrue(exception.getMessage().contains("Failed to call Ollama"));
    }

    @Test
    void generateJson_WithSchemaAndTargetType_ParsesStructuredPayload() throws Exception {
                OllamaProperties properties = new OllamaProperties();
                HttpClient httpClient = Mockito.mock(HttpClient.class);
                @SuppressWarnings("unchecked")
                HttpResponse<String> response = Mockito.mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(200);
                when(response.body()).thenReturn(
                                "{\"response\":\"{\\\"searchQueries\\\":[\\\"coffee\\\"],"
                                + "\\\"keywords\\\":[\\\"coffee\\\"],"
                                + "\\\"category\\\":null,\\\"vibe\\\":null,\\\"locationHint\\\":null}\"}"
                );
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                                .thenReturn(response);

                OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

                PlaceSearchExtraction extraction = client.generateJson(
                                "sys",
                                "user",
                                """
                                {
                                    "type":"object",
                                    "properties":{
                                        "searchQueries":{"type":"array","items":{"type":"string"}},
                                        "keywords":{"type":"array","items":{"type":"string"}},
                                        "category":{"type":["string","null"]},
                                        "vibe":{"type":["string","null"]},
                                        "locationHint":{"type":["string","null"]}
                                    },
                                    "required":["searchQueries","keywords","category","vibe","locationHint"],
                                    "additionalProperties":false
                                }
                                """,
                                PlaceSearchExtraction.class
                );

                assertEquals("coffee", extraction.searchQueries().getFirst());
    }

    @Test
    void generateJson_WithSchemaAndTargetType_ThrowsOnWrappedStructuredPayload() throws Exception {
                OllamaProperties properties = new OllamaProperties();
                HttpClient httpClient = Mockito.mock(HttpClient.class);
                @SuppressWarnings("unchecked")
                HttpResponse<String> response = Mockito.mock(HttpResponse.class);
                when(response.statusCode()).thenReturn(200);
                when(response.body()).thenReturn(
                                "{\"response\":\"```json\\n{\\\"searchQueries\\\":[\\\"coffee\\\"],"
                                + "\\\"keywords\\\":[\\\"coffee\\\"],"
                                + "\\\"category\\\":null,\\\"vibe\\\":null,\\\"locationHint\\\":null}\\n```\"}"
                );
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                                .thenReturn(response);

                OllamaJsonClient client = new OllamaJsonClient(httpClient, new ObjectMapper(), properties);

                AiParseException exception = assertThrows(
                                AiParseException.class,
                                () -> client.generateJson(
                                                "sys",
                                                "user",
                                                """
                                                {
                                                    "type":"object",
                                                    "properties":{
                                                        "searchQueries":{"type":"array","items":{"type":"string"}},
                                                        "keywords":{"type":"array","items":{"type":"string"}},
                                                        "category":{"type":["string","null"]},
                                                        "vibe":{"type":["string","null"]},
                                                        "locationHint":{"type":["string","null"]}
                                                    },
                                                    "required":["searchQueries","keywords","category","vibe","locationHint"],
                                                    "additionalProperties":false
                                                }
                                                """,
                                                PlaceSearchExtraction.class
                                )
                );
                                        assertTrue(exception.getMessage().contains("Failed to decode Ollama structured output"));
    }

    private JsonNode readTree(ObjectMapper objectMapper, String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new AssertionError("Expected valid JSON request body", e);
        }
    }
}
