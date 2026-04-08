package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.RearrangeStopInput;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripGraphqlControllerTest {

    @Mock
    private TripService tripService;

    private TripGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new TripGraphqlController(tripService);
    }

    @Test
    void tripPlans_ReturnsData() {
        TripPlan plan = new TripPlan();
        when(tripService.getAll()).thenReturn(List.of(plan));

        List<TripPlan> result = controller.tripPlans();

        assertEquals(1, result.size());
    }

    @Test
    void tripPlan_WhenFound_ReturnsEntity() {
        TripPlan plan = new TripPlan();
        when(tripService.getById("t1")).thenReturn(Optional.of(plan));

        TripPlan result = controller.tripPlan("t1");

        assertSame(plan, result);
    }

    @Test
    void tripPlan_WhenMissing_ReturnsNull() {
        when(tripService.getById("t1")).thenReturn(Optional.empty());

        assertNull(controller.tripPlan("t1"));
    }

    @Test
    void tripPlansByGroup_ReturnsData() {
        TripPlan plan = new TripPlan();
        when(tripService.getByGroupId("g1")).thenReturn(List.of(plan));

        List<TripPlan> result = controller.tripPlansByGroup("g1");

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
    void addTripStop_WhenFound_ReturnsResult() {
        TripPlan plan = new TripPlan();
        TripStop stop = new TripStop();
        stop.setTitle("Park");
        when(tripService.addStop("t1", stop)).thenReturn(Optional.of(plan));

        TripPlan result = controller.addTripStop("t1", stop);

        assertSame(plan, result);
    }

    @Test
    void addTripStop_WhenMissing_ReturnsNull() {
        when(tripService.addStop(eq("t1"), any())).thenReturn(Optional.empty());

        assertNull(controller.addTripStop("t1", new TripStop()));
    }

    @Test
    void removeTripStop_WhenFound_ReturnsResult() {
        TripPlan plan = new TripPlan();
        when(tripService.removeStop("t1", "s1")).thenReturn(Optional.of(plan));

        TripPlan result = controller.removeTripStop("t1", "s1");

        assertSame(plan, result);
    }

    @Test
    void removeTripStop_WhenMissing_ReturnsNull() {
        when(tripService.removeStop("t1", "s1")).thenReturn(Optional.empty());

        assertNull(controller.removeTripStop("t1", "s1"));
    }

    @Test
    void rearrangeTripStops_WhenFound_ReturnsResult() {
        TripPlan plan = new TripPlan();
        RearrangeStopInput input = new RearrangeStopInput();
        input.setId("s1");
        input.setOrderIndex(0);
        List<RearrangeStopInput> stops = List.of(input);
        when(tripService.rearrangeStops("t1", stops)).thenReturn(Optional.of(plan));

        TripPlan result = controller.rearrangeTripStops("t1", stops);

        assertSame(plan, result);
    }

    @Test
    void rearrangeTripStops_WhenMissing_ReturnsNull() {
        when(tripService.rearrangeStops(eq("t1"), any())).thenReturn(Optional.empty());

        assertNull(controller.rearrangeTripStops("t1", List.of()));
    }

    @Test
    void deleteTripPlan_DelegatesToService() {
        when(tripService.deleteById("t1")).thenReturn(true);

        assertTrue(controller.deleteTripPlan("t1"));
    }
}
