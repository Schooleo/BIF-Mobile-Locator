package com.bif.server.features.route.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bif.server.features.route.dto.rest.RouteRequest;
import com.bif.server.features.route.dto.rest.RouteResponse;
import com.bif.server.features.route.services.OsrmRouteService;
import com.bif.server.features.route.services.RouteNotFoundException;

@RestController
@RequestMapping("/api/routes")
public class RouteRestController {

    private static final Logger LOG = LoggerFactory.getLogger(RouteRestController.class);

    private final OsrmRouteService osrmRouteService;

    public RouteRestController(OsrmRouteService osrmRouteService) {
        this.osrmRouteService = osrmRouteService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> route(@RequestBody RouteRequest request) {
        if (request == null || request.waypoints() == null) {
            LOG.warn("Route request rejected: missing request body or waypoints");
            return ResponseEntity.badRequest().build();
        }

        LOG.info(
                "Route request received: profile={}, waypoints={}",
                request.profile(),
                request.waypoints().size());

        try {
            RouteResponse response = osrmRouteService.route(
                    request.waypoints(), request.profile());

            LOG.info(
                    "Route request succeeded: profile={}, distanceMeters={}, durationSeconds={}",
                    response.profile(),
                    response.distanceMeters(),
                    response.durationSeconds());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            LOG.warn("Route request invalid: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (RouteNotFoundException ex) {
            LOG.warn("Route not found: {}", ex.getMessage());
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            LOG.error("Route request failed with internal error", ex);
            return ResponseEntity.internalServerError().build();
        }
    }
}
