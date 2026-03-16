package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
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
class FavoriteRestControllerTest {

    @Mock
    private FavoriteService favoriteService;

    private FavoriteRestController controller;

    @BeforeEach
    void setUp() {
        controller = new FavoriteRestController(favoriteService);
    }

    @Test
    void getFavorites_ReturnsData() {
        Favorite item = new Favorite();
        when(favoriteService.getAll()).thenReturn(List.of(item));

        List<Favorite> result = controller.getFavorites();

        assertEquals(1, result.size());
    }

    @Test
    void getFavoriteById_WhenFound_ReturnsOk() {
        Favorite item = new Favorite();
        when(favoriteService.getById("f1")).thenReturn(Optional.of(item));

        ResponseEntity<Favorite> result = controller.getFavoriteById("f1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(item, result.getBody());
    }

    @Test
    void getFavoriteById_WhenMissing_ReturnsNotFound() {
        when(favoriteService.getById("f1")).thenReturn(Optional.empty());

        ResponseEntity<Favorite> result = controller.getFavoriteById("f1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getFavoritesByUser_ReturnsData() {
        Favorite item = new Favorite();
        when(favoriteService.getByUserId("u1")).thenReturn(List.of(item));

        List<Favorite> result = controller.getFavoritesByUser("u1");

        assertEquals(1, result.size());
    }

    @Test
    void upsertFavorite_DelegatesToService() {
        Favorite input = new Favorite();
        when(favoriteService.save(input)).thenReturn(input);

        Favorite result = controller.upsertFavorite(input);

        assertSame(input, result);
    }

    @Test
    void deleteFavorite_WhenDeleted_ReturnsNoContent() {
        when(favoriteService.deleteById("f1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteFavorite("f1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteFavorite_WhenMissing_ReturnsNotFound() {
        when(favoriteService.deleteById("f1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteFavorite("f1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
