package com.bif.server.features.route.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.bif.server.common.models.Location;
import com.bif.server.features.route.dto.rest.RouteRequest;
import com.bif.server.features.route.dto.rest.RouteResponse;
import com.bif.server.features.route.services.OsrmRouteService;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RouteRestControllerTest {

    @Mock
    private OsrmRouteService osrmRouteService;

    private RouteRestController controller;

    @BeforeEach
    void setUp() {
        controller = new RouteRestController(osrmRouteService);
    }

    @Test
    void route_WhenValidRequest_ReturnsOk() throws Exception {
        RouteRequest request = new RouteRequest(List.of(
                new Location(10.76, 106.66),
                new Location(10.77, 106.70)
        ), "car");

        RouteResponse response = new RouteResponse(
                1200.0,
                240.0,
                new ObjectMapper().readTree("{\"type\":\"LineString\",\"coordinates\":[[106.66,10.76],[106.70,10.77]]}"),
                "driving"
        );
        when(osrmRouteService.route(request.waypoints(), request.profile()))
                .thenReturn(response);

        ResponseEntity<RouteResponse> result = controller.route(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1200.0, result.getBody().distanceMeters());
    }

    @Test
    void route_WhenInvalidRequest_ReturnsBadRequest() {
        RouteRequest request = new RouteRequest(List.of(), "car");
        when(osrmRouteService.route(request.waypoints(), request.profile()))
                .thenThrow(new IllegalArgumentException("At least two waypoints are required"));

        ResponseEntity<RouteResponse> result = controller.route(request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
    }
}
