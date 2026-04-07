package com.bif.server.features.place.controllers;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.services.PlaceService;
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
class PlaceGraphqlControllerTest {

    @Mock
    private PlaceService placeService;

    private PlaceGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaceGraphqlController(placeService);
    }

    @Test
    void places_ReturnsAll() {
        Place place = new Place();
        when(placeService.getAll()).thenReturn(List.of(place));

        List<Place> result = controller.places();

        assertEquals(1, result.size());
    }

    @Test
    void place_WhenFound_ReturnsPlace() {
        Place place = new Place();
        when(placeService.getById("p1")).thenReturn(Optional.of(place));

        Place result = controller.place("p1");

        assertSame(place, result);
    }

    @Test
    void place_WhenMissing_ReturnsNull() {
        when(placeService.getById("p1")).thenReturn(Optional.empty());

        Place result = controller.place("p1");

        assertNull(result);
    }

    @Test
    void searchPlaces_DelegatesToService() {
        Place place = new Place();
        when(placeService.search("cathedral")).thenReturn(List.of(place));

        List<Place> result = controller.searchPlaces("cathedral");

        assertEquals(1, result.size());
        verify(placeService).search("cathedral");
    }

    @Test
    void placesByTag_DelegatesToService() {
        Place place = new Place();
        when(placeService.getByTag("church")).thenReturn(List.of(place));

        List<Place> result = controller.placesByTag("church");

        assertEquals(1, result.size());
        verify(placeService).getByTag("church");
    }

    @Test
    void placesByUser_DelegatesToService() {
        Place place = new Place();
        when(placeService.getByUserId("u1")).thenReturn(List.of(place));

        List<Place> result = controller.placesByUser("u1");

        assertEquals(1, result.size());
        verify(placeService).getByUserId("u1");
    }

    @Test
    void upsertPlace_DelegatesToService() {
        Place input = new Place();
        when(placeService.save(input)).thenReturn(input);

        Place result = controller.upsertPlace(input);

        assertSame(input, result);
    }

    @Test
    void saveFromSearch_DelegatesToService() {
        Place input = new Place();
        when(placeService.saveFromSearch(input)).thenReturn(input);

        Place result = controller.saveFromSearch(input);

        assertSame(input, result);
        verify(placeService).saveFromSearch(input);
    }

    @Test
    void deletePlace_DelegatesToService() {
        when(placeService.deleteById("p1")).thenReturn(true);

        Boolean result = controller.deletePlace("p1");

        assertTrue(result);
    }
}
