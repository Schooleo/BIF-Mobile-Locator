package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

@Component
public class TypesensePlaceIndexSyncService implements PlaceSearchIndexSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TypesensePlaceIndexSyncService.class);

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 500;
    private static final String DEFAULT_PROTOCOL = "http";
    private static final String DEFAULT_HOST = "localhost";

    private final TypesenseProperties typesenseProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

        @Autowired
        public TypesensePlaceIndexSyncService(TypesenseProperties typesenseProperties,
            ObjectMapper objectMapper) {
        this.typesenseProperties = typesenseProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(
                        typesenseProperties.getConnectTimeoutMs()))
                .build();
    }

        // Constructor overload for tests to inject a custom HttpClient
        TypesensePlaceIndexSyncService(TypesenseProperties typesenseProperties,
                       ObjectMapper objectMapper,
                       HttpClient httpClient) {
        this.typesenseProperties = typesenseProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient != null ? httpClient : HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(
                typesenseProperties.getConnectTimeoutMs()))
            .build();
        }

    @Override
    public void upsert(Place place) {
        if (!isReady() || place == null || place.getId() == null
                || place.getId().isBlank()) {
            return;
        }

        try {
            String body = objectMapper.writeValueAsString(place);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUpsertUri())
                    .timeout(Duration.ofMillis(
                            typesenseProperties.getReadTimeoutMs()))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            sendWithRetry(request, "upsert place " + place.getId());
        } catch (IOException e) {
            LOGGER.error("Typesense upsert I/O failure for place {}", place.getId(), e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Typesense upsert URI configuration error for place {}", place.getId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense upsert interrupted for place {}", place.getId(), e);
        }
    }

    /**
     * Bulk-import a list of places using Typesense's JSONL import endpoint.
     * Far more efficient than individual upserts for bootstrap scenarios.
     *
     * @return the number of successfully imported documents
     */
    @Override
    public int batchUpsert(List<Place> places) {
        if (!isReady() || places == null || places.isEmpty()) {
            return 0;
        }

        try {
            // Build JSONL body (one JSON object per line)
            StringJoiner joiner = new StringJoiner("\n");
            for (Place place : places) {
                if (place != null && place.getId() != null && !place.getId().isBlank()) {
                    joiner.add(objectMapper.writeValueAsString(place));
                }
            }
            String jsonlBody = joiner.toString();
            if (jsonlBody.isBlank()) {
                return 0;
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildImportUri())
                    .timeout(Duration.ofSeconds(30))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonlBody))
                    .build();

            HttpResponse<String> response = sendWithRetry(request, "batch import");
            if (response != null && response.statusCode() >= 200 && response.statusCode() < 300) {
                // Count successful lines (each line is a JSON result)
                int successCount = 0;
                for (String line : response.body().split("\n")) {
                    if (line.contains("\"success\":true")) {
                        successCount++;
                    }
                }
                return successCount;
            }
        } catch (IOException e) {
            LOGGER.error("Typesense batch import I/O failure", e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Typesense batch import URI configuration error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense batch import interrupted", e);
        }
        return 0;
    }

    /**
     * Send an HTTP request with retry + exponential backoff for transient errors
     * (503).
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request, String context)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 503 && attempt < MAX_RETRIES) {
                long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
                LOGGER.warn("Typesense 503 for {}. Retrying in {}ms (attempt {}/{})...",
                        context, backoff, attempt + 1, MAX_RETRIES);
                Thread.sleep(backoff);
                continue;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Typesense {} failed with status {}", context, response.statusCode());
            }
            return response;
        }
        return null;
    }

    @Override
    public void deleteById(String placeId) {
        if (!isReady() || placeId == null || placeId.isBlank()) {
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildDeleteUri(placeId))
                    .timeout(Duration.ofMillis(
                            typesenseProperties.getReadTimeoutMs()))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Typesense delete failed for place {} with status {}",
                        placeId, response.statusCode());
            }
        } catch (IOException e) {
            LOGGER.error("Typesense delete I/O failure for place {}", placeId, e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Typesense delete URI configuration error for place {}", placeId, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense delete interrupted for place {}", placeId, e);
        }
    }

    private boolean isReady() {
        if (!typesenseProperties.isEnabled()) {
            return false;
        }
        if (typesenseProperties.getApiKey() == null
                || typesenseProperties.getApiKey().isBlank()) {
            LOGGER.warn("Skipping Typesense index sync because API key is empty");
            return false;
        }
        return true;
    }

    /**
     * Creates the Typesense collection if it does not already exist.
     * Waits for Typesense to become healthy before proceeding.
     */
    @Override
    public void ensureCollectionExists() {
        if (!isReady()) {
            return;
        }

        String collection = safe(typesenseProperties.getPlacesCollection(), "places");
        String baseUrl = buildBaseUrl();

        try {
            // 1. Wait for Typesense to be healthy
            if (!waitForTypesenseReady(baseUrl)) {
                LOGGER.error("Typesense did not become ready in time. Skipping collection setup.");
                return;
            }

            // 2. Check if collection already exists
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + encode(collection)))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .GET()
                    .build();

            HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getResp.statusCode() == 200) {
                LOGGER.info("Typesense collection '{}' already exists.", collection);
                return;
            }

            // 3. Create collection with schema matching the Place model (NO ID FIELD)
            String schema = """
                    {
                      "name": "%s",
                      "fields": [
                        {"name": "name",              "type": "string"},
                        {"name": "address",           "type": "string",   "optional": true},
                        {"name": "rating",            "type": "float",    "optional": true},
                        {"name": "tags",              "type": "string[]", "optional": true},
                        {"name": "placeSource",       "type": "string",   "optional": true},
                        {"name": "persistedByAction", "type": "string",   "optional": true},
                        {"name": "persistedByUserId", "type": "string",   "optional": true},
                        {"name": "reviewCount",       "type": "int32",    "optional": true}
                      ]
                    }
                    """.formatted(collection);

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections"))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(schema))
                    .build();

            HttpResponse<String> postResp = sendWithRetry(postReq, "create collection '" + collection + "'");
            if (postResp != null && postResp.statusCode() >= 200 && postResp.statusCode() < 300) {
                LOGGER.info("✅ Created Typesense collection '{}'.", collection);
            } else {
                LOGGER.error("Failed to create Typesense collection '{}': {}",
                        collection,
                        postResp != null ? postResp.statusCode() + " - " + postResp.body() : "null response");
            }
        } catch (IOException e) {
            LOGGER.error("I/O error ensuring Typesense collection exists", e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid Typesense URI configuration. Skipping collection setup.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Interrupted while ensuring Typesense collection exists", e);
        }
    }

    /**
     * Polls Typesense /health endpoint until it responds 200 or max attempts are
     * exhausted.
     */
    private boolean waitForTypesenseReady(String baseUrl) throws InterruptedException {
        int maxAttempts = 10;
        long waitMs = 2000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest healthReq = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/health"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(healthReq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    LOGGER.info("Typesense is healthy.");
                    return true;
                }
                LOGGER.warn("Typesense health check returned {}. Waiting {}ms (attempt {}/{})...",
                        resp.statusCode(), waitMs, attempt, maxAttempts);
            } catch (IOException e) {
                LOGGER.warn("Typesense not reachable yet: {}. Waiting {}ms (attempt {}/{})...",
                        e.getMessage(), waitMs, attempt, maxAttempts);
            } catch (IllegalArgumentException e) {
                LOGGER.error("Invalid Typesense health URI '{}': {}", baseUrl + "/health", e.getMessage());
                return false;
            }
            Thread.sleep(waitMs);
        }
        return false;
    }

    private URI buildUpsertUri() {
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String uri = String.format("%s/collections/%s/documents?action=upsert",
            buildBaseUrl(),
                collection);
        return URI.create(uri);
    }

    private URI buildImportUri() {
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String uri = String.format("%s/collections/%s/documents/import?action=upsert",
            buildBaseUrl(),
                collection);
        return URI.create(uri);
    }

    private URI buildDeleteUri(String placeId) {
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String encodedPlaceId = encode(placeId);
        String uri = String.format("%s/collections/%s/documents/%s",
                buildBaseUrl(),
                collection,
                encodedPlaceId);
        return URI.create(uri);
    }

    private String buildBaseUrl() {
        return String.format("%s://%s:%d",
                normalizedProtocol(),
                safe(typesenseProperties.getHost(), DEFAULT_HOST).trim(),
                typesenseProperties.getPort());
    }

    private String normalizedProtocol() {
        String raw = safe(typesenseProperties.getProtocol(), DEFAULT_PROTOCOL);
        String protocol = raw.trim().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            LOGGER.warn("Invalid typesense.protocol '{}' configured. Falling back to '{}'.", raw, DEFAULT_PROTOCOL);
            return DEFAULT_PROTOCOL;
        }
        return protocol;
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}