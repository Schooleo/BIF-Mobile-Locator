package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
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
    void deleteTripPlan_DelegatesToService() {
        when(tripService.deleteById("t1")).thenReturn(true);

        assertTrue(controller.deleteTripPlan("t1"));
    }
}
