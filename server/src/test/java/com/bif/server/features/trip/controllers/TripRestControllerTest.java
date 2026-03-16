package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.services.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripRestControllerTest {

    @Mock
    private TripService tripService;

    private TripRestController controller;

    @BeforeEach
    void setUp() {
        controller = new TripRestController(tripService);
    }

    @Test
    void getTripPlans_ReturnsData() {
        TripPlan plan = new TripPlan();
        when(tripService.getAll()).thenReturn(List.of(plan));

        List<TripPlan> result = controller.getTripPlans();

        assertEquals(1, result.size());
    }

    @Test
    void getTripPlanById_WhenFound_ReturnsOk() {
        TripPlan plan = new TripPlan();
        when(tripService.getById("t1")).thenReturn(Optional.of(plan));

        ResponseEntity<TripPlan> result = controller.getTripPlanById("t1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(plan, result.getBody());
    }

    @Test
    void getTripPlanById_WhenMissing_ReturnsNotFound() {
        when(tripService.getById("t1")).thenReturn(Optional.empty());

        ResponseEntity<TripPlan> result = controller.getTripPlanById("t1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getTripPlansByGroup_ReturnsData() {
        TripPlan plan = new TripPlan();
        when(tripService.getByGroupId("g1")).thenReturn(List.of(plan));

        List<TripPlan> result = controller.getTripPlansByGroup("g1");

        assertEquals(1, result.size());
    }

    @Test
    void upsertTripPlan_DelegatesToService() {
        TripPlan input = new TripPlan();
        when(tripService.save(input)).thenReturn(input);

        TripPlan result = controller.upsertTripPlan(input);

        assertSame(input, result);
    }

    @Test
    void deleteTripPlan_WhenDeleted_ReturnsNoContent() {
        when(tripService.deleteById("t1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteTripPlan("t1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteTripPlan_WhenMissing_ReturnsNotFound() {
        when(tripService.deleteById("t1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteTripPlan("t1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
