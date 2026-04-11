package com.bif.server.features.ai.clients;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.bif.server.features.ai.config.OllamaProperties;
import com.bif.server.features.ai.exceptions.AiParseException;
import com.bif.server.features.ai.exceptions.AiUpstreamException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OllamaJsonClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OllamaProperties ollamaProperties;

    public OllamaJsonClient(
            @Qualifier("ollamaHttpClient") HttpClient httpClient,
            ObjectMapper objectMapper,
            OllamaProperties ollamaProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.ollamaProperties = ollamaProperties;
    }

    public String generateJson(String systemPrompt, String userPrompt) {
        return generate(systemPrompt, userPrompt, "json");
    }

    public String generateJson(String systemPrompt, String userPrompt, String jsonSchema) {
        try {
            JsonNode schemaNode = objectMapper.readTree(jsonSchema);
            return generate(systemPrompt, userPrompt, schemaNode);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON schema format", e);
        }
    }

    private String generate(String systemPrompt, String userPrompt, Object format) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(resolveGenerateEndpoint()))
                .timeout(Duration.ofMillis(ollamaProperties.getTimeoutMs()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildRequestBody(systemPrompt, userPrompt, format),
                        StandardCharsets.UTF_8))
                .build();

        String rawBody = null;
        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiUpstreamException(
                        "Ollama request failed with status " + response.statusCode());
            }

            rawBody = response.body();
            JsonNode payload = objectMapper.readTree(rawBody);
            JsonNode errorNode = payload.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                throw new AiUpstreamException(
                        "Ollama returned an error payload: " + textOrSnippet(errorNode.asText()));
            }

            JsonNode responseNode = payload.path("response");
            if (!responseNode.isTextual() || responseNode.asText().isBlank()) {
                throw new AiUpstreamException("Ollama returned an empty response payload");
            }
            return responseNode.asText();
        } catch (JsonProcessingException e) {
            throw new AiParseException(
                    "Failed to decode Ollama transport payload: " + snippet(rawBody),
                    e
            );
        } catch (HttpTimeoutException e) {
            throw new AiUpstreamException(
                "Ollama request timed out after "
                    + ollamaProperties.getTimeoutMs()
                    + " ms",
                e);
        } catch (IOException e) {
            throw new AiUpstreamException("Failed to call Ollama", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUpstreamException("Ollama request was interrupted", e);
        }
    }

    private String textOrSnippet(String value) {
        if (value == null || value.isBlank()) {
            return "unknown upstream error";
        }
        return snippet(value);
    }

    private String snippet(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 157) + "...";
    }

    String buildRequestBody(String systemPrompt, String userPrompt) {
        return buildRequestBody(systemPrompt, userPrompt, (Object) "json");
    }

    String buildRequestBody(String systemPrompt, String userPrompt, String jsonSchema) {
        try {
            return buildRequestBody(systemPrompt, userPrompt, objectMapper.readTree(jsonSchema));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON schema format", e);
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, Object format) {
        GenerateRequest request = new GenerateRequest(
                ollamaProperties.getModel(),
                userPrompt,
                systemPrompt,
                false,
                format,
                new GenerateOptions(ollamaProperties.getTemperature()));
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Ollama request", e);
        }
    }

    private String resolveGenerateEndpoint() {
        String baseUrl = ollamaProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/api/generate";
    }

    private record GenerateRequest(
            String model,
            String prompt,
            String system,
            boolean stream,
            Object format,
            GenerateOptions options) {
    }

    private record GenerateOptions(double temperature) {
    }

}
