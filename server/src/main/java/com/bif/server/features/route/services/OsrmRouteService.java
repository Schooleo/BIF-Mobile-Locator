package com.bif.server.features.route.services;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.bif.server.common.models.Location;
import com.bif.server.features.route.dto.rest.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OsrmRouteService {

    private static final Logger LOG = LoggerFactory.getLogger(OsrmRouteService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OsrmRouteService(
            @Value("${routing.osrm.base-url:http://localhost:5000}") String osrmBaseUrl,
            ObjectMapper objectMapper) {
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

        LOG.info("Calling OSRM: profile={}, waypoints={}, uri={}",
                profile, waypoints.size(), uri);

        try {
            String body = restClient.get()
                    .uri(uri)
                    // Force plain response bytes to avoid RestClient decompression errors.
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new IllegalStateException("Empty OSRM response");
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode() || route.isNull()) {
                throw new RouteNotFoundException("No route found in OSRM response");
            }

            double distanceMeters = route.path("distance").asDouble(0.0);
            double durationSeconds = route.path("duration").asDouble(0.0);
            JsonNode geometry = route.path("geometry");

            if (geometry.isMissingNode() || geometry.isNull()) {
                throw new IllegalStateException("Missing route geometry in OSRM response");
            }

            Object geometryPayload = objectMapper.convertValue(geometry, Object.class);

            LOG.info("OSRM route resolved: profile={}, distanceMeters={}, durationSeconds={}",
                    profile, distanceMeters, durationSeconds);

            return new RouteResponse(distanceMeters, durationSeconds, geometryPayload, profile);
        } catch (RouteNotFoundException ex) {
            LOG.warn("OSRM returned no route: profile={}, waypoints={}", profile, waypoints.size());
            throw ex;
        } catch (RestClientResponseException ex) {
            LOG.warn(
                    "OSRM HTTP error: status={}, profile={}, waypoints={}, body={} ",
                    ex.getStatusCode().value(),
                    profile,
                    waypoints.size(),
                    truncate(ex.getResponseBodyAsString()));
            if (isNoRouteResponse(ex)) {
                throw new RouteNotFoundException("No route found in OSRM response");
            }
            throw new IllegalStateException("OSRM error response: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            LOG.error("OSRM call failed: profile={}, waypoints={}", profile, waypoints.size(), ex);
            throw new IllegalStateException("Failed to query OSRM", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        int maxLength = 300;
        return value.length() > maxLength
                ? value.substring(0, maxLength) + "..."
                : value;
    }

    private boolean isNoRouteResponse(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status != 400 && status != 404) {
            return false;
        }

        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return false;
        }

        String normalized = body.toLowerCase(Locale.ROOT);
        return normalized.contains("noroute") || normalized.contains("no route");
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
