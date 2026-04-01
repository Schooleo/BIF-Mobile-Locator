package com.bif.server.features.route.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.route.dto.rest.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class OsrmRouteService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OsrmRouteService(
            @Value("${routing.osrm.base-url:http://localhost:5000}")
            String osrmBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(osrmBaseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    public RouteResponse route(List<Location> waypoints, String requestedProfile) {
        if (waypoints == null || waypoints.size() < 2) {
            throw new IllegalArgumentException("At least two waypoints are required");
        }

        String profile = resolveOsrmProfile(requestedProfile);
        String coordinates = waypoints.stream()
                .map(this::toOsrmCoordinate)
                .collect(Collectors.joining(";"));

        String uri = "/route/v1/" + profile + "/" + coordinates
                + "?geometries=geojson&overview=full";

        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Empty OSRM response");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode() || route.isNull()) {
                throw new IllegalStateException("No route found in OSRM response");
            }

            double distanceMeters = route.path("distance").asDouble(0.0);
            double durationSeconds = route.path("duration").asDouble(0.0);
            JsonNode geometry = route.path("geometry");

            if (geometry.isMissingNode() || geometry.isNull()) {
                throw new IllegalStateException("Missing route geometry in OSRM response");
            }

            return new RouteResponse(distanceMeters, durationSeconds, geometry, profile);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to query OSRM", ex);
        }
    }

    private String toOsrmCoordinate(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Waypoint cannot be null");
        }

        // OSRM expects longitude,latitude order.
        return location.getLongitude() + "," + location.getLatitude();
    }

    private String resolveOsrmProfile(String requestedProfile) {
        if (requestedProfile == null || requestedProfile.isBlank()) {
            return "driving";
        }

        String profile = requestedProfile.trim().toLowerCase(Locale.ROOT);
        switch (profile) {
            case "car":
            case "driving":
                return "driving";
            case "bike":
            case "bicycle":
            case "cycling":
                return "cycling";
            case "foot":
            case "walk":
            case "walking":
                return "walking";
            default:
                return "driving";
        }
    }
}


