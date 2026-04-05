package com.bif.server.features.place.controllers;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.services.PlaceService;
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
class PlaceRestControllerTest {

    @Mock
    private PlaceService placeService;

    @Mock
    private com.bif.server.features.place.services.PlaceIdentityService placeIdentityService;

    private PlaceRestController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaceRestController(placeService, placeIdentityService);
    }

    @Test
    void getPlaces_ReturnsData() {
        Place item = new Place();
        when(placeService.getAll()).thenReturn(List.of(item));

        List<Place> result = controller.getPlaces();

        assertEquals(1, result.size());
    }

    @Test
    void getPlaceById_WhenFound_ReturnsOk() {
        Place item = new Place();
        when(placeService.getById("p1")).thenReturn(Optional.of(item));

        ResponseEntity<Place> result = controller.getPlaceById("p1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(item, result.getBody());
    }

    @Test
    void getPlaceById_WhenMissing_ReturnsNotFound() {
        when(placeService.getById("p1")).thenReturn(Optional.empty());

        ResponseEntity<Place> result = controller.getPlaceById("p1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void searchPlaces_DelegatesToService() {
        Place item = new Place();
        when(placeService.search("test")).thenReturn(List.of(item));

        List<Place> result = controller.searchPlaces("test");

        assertEquals(1, result.size());
        verify(placeService).search("test");
    }

    @Test
    void getByTag_DelegatesToService() {
        Place item = new Place();
        when(placeService.getByTag("church")).thenReturn(List.of(item));

        List<Place> result = controller.getByTag("church");

        assertEquals(1, result.size());
        verify(placeService).getByTag("church");
    }

    @Test
    void getByUser_DelegatesToService() {
        Place item = new Place();
        when(placeService.getByUserId("u1")).thenReturn(List.of(item));

        List<Place> result = controller.getByUser("u1");

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
    void deletePlace_WhenDeleted_ReturnsNoContent() {
        when(placeService.deleteById("p1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deletePlace("p1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deletePlace_WhenMissing_ReturnsNotFound() {
        when(placeService.deleteById("p1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deletePlace("p1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
