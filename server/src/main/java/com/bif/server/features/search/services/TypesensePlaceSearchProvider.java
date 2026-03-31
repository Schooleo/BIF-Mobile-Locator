package com.bif.server.features.search.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class TypesensePlaceSearchProvider implements PlaceSearchProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TypesensePlaceSearchProvider.class);

    private final TypesenseProperties typesenseProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

        @Autowired
        public TypesensePlaceSearchProvider(TypesenseProperties typesenseProperties,
                        ObjectMapper objectMapper) {
        this.typesenseProperties = typesenseProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(
                        typesenseProperties.getConnectTimeoutMs()))
                .build();
    }


        // Constructor overload for tests to inject a custom HttpClient
        TypesensePlaceSearchProvider(TypesenseProperties typesenseProperties,
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
    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        if (!typesenseProperties.isEnabled()) {
            LOGGER.warn("Typesense provider selected but typesense.enabled=false");
            return Collections.emptyList();
        }

        if (typesenseProperties.getApiKey() == null
                || typesenseProperties.getApiKey().isBlank()) {
            LOGGER.warn("Typesense provider selected but typesense.api-key is empty");
            return Collections.emptyList();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildSearchUri(query))
                    .timeout(Duration.ofMillis(
                            typesenseProperties.getReadTimeoutMs()))
                    .header("X-TYPESENSE-API-KEY", typesenseProperties.getApiKey())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("Typesense search failed with status {}", response.statusCode());
                return Collections.emptyList();
            }

            return parsePlaces(response.body());
        } catch (IOException e) {
            LOGGER.error("Typesense search I/O failure", e);
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Typesense search interrupted", e);
            return Collections.emptyList();
        }
    }

    private URI buildSearchUri(String query) {
        String protocol = safe(typesenseProperties.getProtocol(), "http");
        String host = safe(typesenseProperties.getHost(), "localhost");
        int port = typesenseProperties.getPort();
        String collection = encode(safe(typesenseProperties.getPlacesCollection(), "places"));
        String encodedQuery = encode(query);

        String uri = String.format(
                "%s://%s:%d/collections/%s/documents/search?q=%s&query_by=name,address&per_page=20",
                protocol,
                host,
                port,
                collection,
                encodedQuery
        );
        return URI.create(uri);
    }

    private List<Place> parsePlaces(String payload) throws IOException {
        JsonNode root = objectMapper.readTree(payload);
        JsonNode hits = root.path("hits");
        if (!hits.isArray()) {
            return Collections.emptyList();
        }

        List<Place> places = new ArrayList<>();
        for (JsonNode hit : hits) {
            JsonNode doc = hit.path("document");
            if (doc.isMissingNode() || doc.isNull()) {
                continue;
            }

            Place place = new Place();
            place.setId(textOrNull(doc, "id"));
            place.setName(textOrDefault(doc, "name", "Unknown Place"));
            place.setAddress(textOrDefault(doc, "address", ""));
            place.setRating(doubleOrDefault(doc, "rating", 0.0));
            place.setPlaceSource(textOrDefault(doc, "placeSource", "typesense"));
            place.setPersistedByAction(textOrNull(doc, "persistedByAction"));
            place.setPersistedByUserId(textOrNull(doc, "persistedByUserId"));

            Location location = parseLocation(doc.path("location"));
            if (location != null) {
                place.setLocation(location);
            }

            places.add(place);
        }
        return places;
    }

    private Location parseLocation(JsonNode locationNode) {
        if (locationNode == null || locationNode.isMissingNode() || locationNode.isNull()) {
            return null;
        }

        if (locationNode.isObject()) {
            Double latitude = firstDouble(locationNode, "latitude", "lat");
            Double longitude = firstDouble(locationNode, "longitude", "lng", "lon");
            if (latitude != null && longitude != null) {
                return new Location(latitude, longitude);
            }
        }

        if (locationNode.isArray() && locationNode.size() >= 2) {
            JsonNode latNode = locationNode.get(0);
            JsonNode lngNode = locationNode.get(1);
            if (latNode != null && lngNode != null
                    && latNode.isNumber() && lngNode.isNumber()) {
                return new Location(latNode.asDouble(), lngNode.asDouble());
            }
        }

        return null;
    }

    private Double firstDouble(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode candidate = node.path(key);
            if (candidate.isNumber()) {
                return candidate.asDouble();
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String textOrDefault(JsonNode node, String key, String fallback) {
        String value = textOrNull(node, key);
        return value != null ? value : fallback;
    }

    private double doubleOrDefault(JsonNode node, String key, double fallback) {
        JsonNode value = node.path(key);
        return value.isNumber() ? value.asDouble() : fallback;
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
