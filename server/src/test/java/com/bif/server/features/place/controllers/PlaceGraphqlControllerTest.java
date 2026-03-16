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
    void places_ReturnsData() {
        Place item = new Place();
        when(placeService.getAll()).thenReturn(List.of(item));

        List<Place> result = controller.places();

        assertEquals(1, result.size());
    }

    @Test
    void place_WhenFound_ReturnsEntity() {
        Place item = new Place();
        when(placeService.getById("p1")).thenReturn(Optional.of(item));

        Place result = controller.place("p1");

        assertSame(item, result);
    }

    @Test
    void place_WhenMissing_ReturnsNull() {
        when(placeService.getById("p1")).thenReturn(Optional.empty());

        assertNull(controller.place("p1"));
    }

    @Test
    void upsertPlace_DelegatesToService() {
        Place input = new Place();
        when(placeService.save(input)).thenReturn(input);

        Place result = controller.upsertPlace(input);

        assertSame(input, result);
    }

    @Test
    void deletePlace_DelegatesToService() {
        when(placeService.deleteById("p1")).thenReturn(true);

        assertTrue(controller.deletePlace("p1"));
    }
}
