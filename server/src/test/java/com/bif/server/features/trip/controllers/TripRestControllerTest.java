package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripActivityService;
import com.bif.server.features.trip.services.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripRestControllerTest {

    @Mock
    private TripService tripService;

    @Mock
    private TripActivityService tripActivityService;

    private TripRestController controller;

    @BeforeEach
    void setUp() {
        controller = new TripRestController(tripService, tripActivityService);
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
    void upsertTripPlan_NewPlan_PostsCreatedActivity() {
        TripPlan input = new TripPlan();
        input.setId(null);
        input.setTitle("Weekend Trip");

        TripPlan saved = new TripPlan();
        saved.setId("t1");
        saved.setTitle("Weekend Trip");
        when(tripService.save(input)).thenReturn(saved);

        TripPlan result = controller.upsertTripPlan(input, "u1");

        assertSame(saved, result);
        verify(tripActivityService).postTripCreated(saved, "u1");
        verify(tripActivityService, never()).postTripUpdated(any(), any());
    }

    @Test
    void upsertTripPlan_ExistingPlan_PostsUpdatedActivity() {
        TripPlan input = new TripPlan();
        input.setId("t1");
        input.setTitle("Weekend Trip");
        when(tripService.save(input)).thenReturn(input);

        controller.upsertTripPlan(input, "u1");

        verify(tripActivityService).postTripUpdated(input, "u1");
        verify(tripActivityService, never()).postTripCreated(any(), any());
    }

    @Test
    void addStop_WhenFound_ReturnsOk() {
        TripPlan plan = new TripPlan();
        plan.setStops(new ArrayList<>());
        TripStop stop = new TripStop();
        stop.setTitle("Central Park");
        when(tripService.addStop("t1", stop)).thenReturn(Optional.of(plan));

        ResponseEntity<TripPlan> result = controller.addStop("t1", stop, "u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(tripActivityService).postStopAdded(plan, stop, "u1");
    }

    @Test
    void addStop_WhenMissing_ReturnsNotFound() {
        when(tripService.addStop(eq("t1"), any())).thenReturn(Optional.empty());

        ResponseEntity<TripPlan> result =
                controller.addStop("t1", new TripStop(), "u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void removeStop_WhenFound_ReturnsOk() {
        TripPlan plan = new TripPlan();
        when(tripService.removeStop("t1", "s1")).thenReturn(Optional.of(plan));

        ResponseEntity<TripPlan> result = controller.removeStop("t1", "s1", "u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(tripActivityService).postStopRemoved(plan, "s1", "u1");
    }

    @Test
    void removeStop_WhenMissing_ReturnsNotFound() {
        when(tripService.removeStop("t1", "s1")).thenReturn(Optional.empty());

        ResponseEntity<TripPlan> result = controller.removeStop("t1", "s1", "u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void rearrangeStops_WhenFound_ReturnsOk() {
        TripPlan plan = new TripPlan();
        TripStop stop = new TripStop();
        stop.setId("s1");
        stop.setOrderIndex(0);
        List<TripStop> stops = List.of(stop);
        when(tripService.rearrangeStops(eq("t1"), any())).thenReturn(Optional.of(plan));

        ResponseEntity<TripPlan> result =
                controller.rearrangeStops("t1", stops, "u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(tripService).rearrangeStops(eq("t1"), argThat(inputs ->
            inputs != null
                && inputs.size() == 1
                && "s1".equals(inputs.get(0).getId())
                && inputs.get(0).getOrderIndex() == 0));
        verify(tripActivityService).postStopsRearranged(plan, "u1");
    }

    @Test
    void rearrangeStops_WhenMissing_ReturnsNotFound() {
        when(tripService.rearrangeStops(eq("t1"), any())).thenReturn(Optional.empty());

        ResponseEntity<TripPlan> result =
                controller.rearrangeStops("t1", List.of(), "u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void deleteTripPlan_WhenFound_ReturnsNoContent() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");
        when(tripService.getById("t1")).thenReturn(Optional.of(plan));
        when(tripService.deleteById("t1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteTripPlan("t1", "u1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(tripActivityService).postTripDeleted("g1", "Weekend Trip", "u1");
    }

    @Test
    void deleteTripPlan_WhenMissing_ReturnsNotFound() {
        when(tripService.getById("t1")).thenReturn(Optional.empty());

        ResponseEntity<Void> result = controller.deleteTripPlan("t1", "u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}

