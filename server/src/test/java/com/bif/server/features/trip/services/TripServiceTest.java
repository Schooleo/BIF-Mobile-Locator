package com.bif.server.features.trip.services;

import com.bif.server.features.trip.exceptions.TripLimitExceededException;
import com.bif.server.features.trip.models.RearrangeStopInput;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void save_WhenCreatingAndGroupAtLimit_Throws() {
        TripPlan plan = new TripPlan();
        plan.setId(null);
        plan.setGroupId("g1");

        when(tripPlanRepository
                .countByGroupIdAndDeletedFalse("g1"))
                .thenReturn(30L);

        assertThrows(TripLimitExceededException.class,
                () -> tripService.save(plan));
        verify(tripPlanRepository, never()).save(any(TripPlan.class));
    }

    @Test
    void addStop_WhenPlanFound_AddsStopAndSaves() {
        TripPlan plan = new TripPlan();
        plan.setStops(new ArrayList<>());
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class))).thenAnswer(i -> i.getArgument(0));

        TripStop stop = new TripStop();
        stop.setTitle("Central Park");

        Optional<TripPlan> result = tripService.addStop("t1", stop);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getStops().size());
        assertEquals(0, result.get().getStops().getFirst().getOrderIndex());
        assertEquals("Central Park", result.get().getStops().getFirst().getTitle());
        verify(tripPlanRepository).save(plan);
    }

    @Test
    void addStop_WhenNullStops_InitializesListAndAdds() {
        TripPlan plan = new TripPlan();
        plan.setStops(null);
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class))).thenAnswer(i -> i.getArgument(0));

        TripStop stop = new TripStop();
        stop.setTitle("First Stop");

        Optional<TripPlan> result = tripService.addStop("t1", stop);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().getStops().size());
    }

    @Test
    void addStop_WhenPlanMissing_ReturnsEmpty() {
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.empty());

        Optional<TripPlan> result = tripService.addStop("t1", new TripStop());

        assertTrue(result.isEmpty());
        verify(tripPlanRepository, never()).save(any());
    }

    @Test
    void removeStop_WhenFound_RemovesAndReindex() {
        TripStop stop0 = new TripStop();
        stop0.setId("s0");
        stop0.setTitle("A");
        stop0.setOrderIndex(0);
        TripStop stop1 = new TripStop();
        stop1.setId("s1");
        stop1.setTitle("B");
        stop1.setOrderIndex(1);
        TripStop stop2 = new TripStop();
        stop2.setId("s2");
        stop2.setTitle("C");
        stop2.setOrderIndex(2);

        TripPlan plan = new TripPlan();
        plan.setStops(new ArrayList<>(List.of(stop0, stop1, stop2)));
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class))).thenAnswer(i -> i.getArgument(0));

        Optional<TripPlan> result = tripService.removeStop("t1", "s1");

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getStops().size());
        assertEquals(0, result.get().getStops().get(0).getOrderIndex());
        assertEquals(1, result.get().getStops().get(1).getOrderIndex());
        assertEquals("A", result.get().getStops().get(0).getTitle());
        assertEquals("C", result.get().getStops().get(1).getTitle());
    }

    @Test
    void removeStop_WhenPlanMissing_ReturnsEmpty() {
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.empty());

        Optional<TripPlan> result = tripService.removeStop("t1", "s1");

        assertTrue(result.isEmpty());
    }

    @Test
    void rearrangeStops_WhenFound_ReordersByIdAndReindex() {
        TripPlan plan = new TripPlan();
        TripStop stopA = new TripStop();
        stopA.setId("s1");
        stopA.setTitle("A");
        stopA.setOrderIndex(0);
        TripStop stopB = new TripStop();
        stopB.setId("s2");
        stopB.setTitle("B");
        stopB.setOrderIndex(1);
        plan.setStops(new ArrayList<>(List.of(stopA, stopB)));
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class))).thenAnswer(i -> i.getArgument(0));

        RearrangeStopInput reorderA = new RearrangeStopInput();
        reorderA.setId("s1");
        reorderA.setOrderIndex(1);
        RearrangeStopInput reorderB = new RearrangeStopInput();
        reorderB.setId("s2");
        reorderB.setOrderIndex(0);

        Optional<TripPlan> result = tripService.rearrangeStops("t1",
                new ArrayList<>(List.of(reorderA, reorderB)));

        assertTrue(result.isPresent());
        assertEquals(2, result.get().getStops().size());
        assertEquals(0, result.get().getStops().get(0).getOrderIndex());
        assertEquals(1, result.get().getStops().get(1).getOrderIndex());
        assertEquals("B", result.get().getStops().get(0).getTitle());
        assertEquals("A", result.get().getStops().get(1).getTitle());
    }

    @Test
    void rearrangeStops_WhenPlanMissing_ReturnsEmpty() {
        when(tripPlanRepository.findById("t1")).thenReturn(Optional.empty());

        Optional<TripPlan> result = tripService.rearrangeStops("t1", List.of());

        assertTrue(result.isEmpty());
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
