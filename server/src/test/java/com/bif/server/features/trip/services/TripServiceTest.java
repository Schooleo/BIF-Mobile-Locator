package com.bif.server.features.trip.services;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.repositories.TripPlanRepository;
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
class TripServiceTest {

    @Mock
    private TripPlanRepository tripPlanRepository;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripPlanRepository);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        TripPlan plan = new TripPlan();
        when(tripPlanRepository.findAll()).thenReturn(List.of(plan));

        List<TripPlan> result = tripService.getAll();

        assertEquals(1, result.size());
        verify(tripPlanRepository).findAll();
    }

    @Test
    void getByGroupId_ReturnsRepositoryData() {
        TripPlan plan = new TripPlan();
        when(tripPlanRepository.findByGroupId("g1")).thenReturn(List.of(plan));

        List<TripPlan> result = tripService.getByGroupId("g1");

        assertEquals(1, result.size());
        verify(tripPlanRepository).findByGroupId("g1");
    }

    @Test
    void getById_ReturnsOptional() {
        TripPlan plan = new TripPlan();
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.of(plan));

        Optional<TripPlan> result = tripService.getById("t1");

        assertTrue(result.isPresent());
        verify(tripPlanRepository).findById("t1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        TripPlan plan = new TripPlan();
        when(tripPlanRepository.save(plan)).thenReturn(plan);

        TripPlan result = tripService.save(plan);

        assertSame(plan, result);
        verify(tripPlanRepository).save(plan);
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(tripPlanRepository.existsById("t1")).thenReturn(true);

        boolean result = tripService.deleteById("t1");

        assertTrue(result);
        verify(tripPlanRepository).deleteById("t1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(tripPlanRepository.existsById("t1")).thenReturn(false);

        boolean result = tripService.deleteById("t1");

        assertFalse(result);
        verify(tripPlanRepository, never()).deleteById(anyString());
    }
}
