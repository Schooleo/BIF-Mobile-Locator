package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
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
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = new PlaceService(placeRepository);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        Place place = new Place();
        when(placeRepository.findAll()).thenReturn(List.of(place));

        List<Place> result = placeService.getAll();

        assertEquals(1, result.size());
        verify(placeRepository).findAll();
    }

    @Test
    void getById_ReturnsOptional() {
        Place place = new Place();
        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));

        Optional<Place> result = placeService.getById("p1");

        assertTrue(result.isPresent());
        verify(placeRepository).findById("p1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        Place place = new Place();
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.save(place);

        assertSame(place, result);
        verify(placeRepository).save(place);
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(placeRepository.existsById("p1")).thenReturn(true);

        boolean result = placeService.deleteById("p1");

        assertTrue(result);
        verify(placeRepository).deleteById("p1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(placeRepository.existsById("p1")).thenReturn(false);

        boolean result = placeService.deleteById("p1");

        assertFalse(result);
        verify(placeRepository, never()).deleteById(anyString());
    }
}
