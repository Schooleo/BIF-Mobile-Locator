package com.bif.server.features.route.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bif.server.features.route.dto.rest.RouteRequest;
import com.bif.server.features.route.dto.rest.RouteResponse;
import com.bif.server.features.route.services.OsrmRouteService;

@RestController
@RequestMapping("/api/routes")
public class RouteRestController {

    private final OsrmRouteService osrmRouteService;

    public RouteRestController(OsrmRouteService osrmRouteService) {
        this.osrmRouteService = osrmRouteService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> route(@RequestBody RouteRequest request) {
        if (request == null || request.waypoints() == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            RouteResponse response = osrmRouteService.route(
                    request.waypoints(), request.profile());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
