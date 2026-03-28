package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Component
public class TypesensePlaceIndexSyncService implements PlaceSearchIndexSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TypesensePlaceIndexSyncService.class);

    private final TypesenseProperties typesenseProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TypesensePlaceIndexSyncService(TypesenseProperties typesenseProperties,
                                          ObjectMapper objectMapper) {
        this.typesenseProperties = typesenseProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
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

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Typesense upsert failed for place {} with status {}",
                        place.getId(), response.statusCode());
            }
        } catch (IOException e) {
            LOGGER.error("Typesense upsert I/O failure for place {}", place.getId(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense upsert interrupted for place {}", place.getId(), e);
        }
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

    private URI buildUpsertUri() {
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String uri = String.format("%s://%s:%d/collections/%s/documents?action=upsert",
                safe(typesenseProperties.getProtocol(), "http"),
                safe(typesenseProperties.getHost(), "localhost"),
                typesenseProperties.getPort(),
                collection);
        return URI.create(uri);
    }

    private URI buildDeleteUri(String placeId) {
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String encodedPlaceId = encode(placeId);
        String uri = String.format("%s://%s:%d/collections/%s/documents/%s",
                safe(typesenseProperties.getProtocol(), "http"),
                safe(typesenseProperties.getHost(), "localhost"),
                typesenseProperties.getPort(),
                collection,
                encodedPlaceId);
        return URI.create(uri);
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