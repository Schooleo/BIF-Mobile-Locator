package com.bif.server.features.place.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PlaceAddressEnrichmentService {

    public static final String ADDRESS_UNAVAILABLE = "Address unavailable";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean geocodingEnabled;
    private final String userAgent;

    public PlaceAddressEnrichmentService(
            @Value("${place.geocoding.base-url:https://nominatim.openstreetmap.org}")
            String geocodingBaseUrl,
            @Value("${place.geocoding.enabled:true}")
            boolean geocodingEnabled,
            @Value("${place.geocoding.user-agent:bif-mobile-app-server/1.0}")
            String userAgent,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(geocodingBaseUrl)
                .build();
        this.geocodingEnabled = geocodingEnabled;
        this.userAgent = userAgent;
        this.objectMapper = objectMapper;
    }

    public String enrichAddress(String currentAddress,
                                Double latitude,
                                Double longitude) {
        if (!isMissingAddress(currentAddress)) {
            return currentAddress;
        }

        if (!geocodingEnabled || latitude == null || longitude == null) {
            return ADDRESS_UNAVAILABLE;
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("format", "jsonv2")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("addressdetails", 0)
                            .build())
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return ADDRESS_UNAVAILABLE;
            }

            JsonNode node = objectMapper.readTree(response);
            JsonNode displayName = node.get("display_name");
            if (displayName != null && !displayName.asText().isBlank()) {
                return displayName.asText();
            }
        } catch (Exception ignored) {
            // Fallback to placeholder when reverse geocoding is unavailable.
        }

        return ADDRESS_UNAVAILABLE;
    }

    public boolean isMissingAddress(String address) {
        if (address == null || address.isBlank()) {
            return true;
        }
        String normalized = address.trim();
        return ADDRESS_UNAVAILABLE.equalsIgnoreCase(normalized)
                || "Unknown Address".equalsIgnoreCase(normalized);
    }
}
