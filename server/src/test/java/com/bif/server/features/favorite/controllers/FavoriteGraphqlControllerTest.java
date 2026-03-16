package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
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
class FavoriteGraphqlControllerTest {

    @Mock
    private FavoriteService favoriteService;

    private FavoriteGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new FavoriteGraphqlController(favoriteService);
    }

    @Test
    void favorites_ReturnsData() {
        Favorite item = new Favorite();
        when(favoriteService.getAll()).thenReturn(List.of(item));

        List<Favorite> result = controller.favorites();

        assertEquals(1, result.size());
    }

    @Test
    void favorite_WhenFound_ReturnsEntity() {
        Favorite item = new Favorite();
        when(favoriteService.getById("f1")).thenReturn(Optional.of(item));

        Favorite result = controller.favorite("f1");

        assertSame(item, result);
    }

    @Test
    void favorite_WhenMissing_ReturnsNull() {
        when(favoriteService.getById("f1")).thenReturn(Optional.empty());

        assertNull(controller.favorite("f1"));
    }

    @Test
    void favoritesByUser_ReturnsData() {
        Favorite item = new Favorite();
        when(favoriteService.getByUserId("u1")).thenReturn(List.of(item));

        List<Favorite> result = controller.favoritesByUser("u1");

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
    void deleteFavorite_DelegatesToService() {
        when(favoriteService.deleteById("f1")).thenReturn(true);

        assertTrue(controller.deleteFavorite("f1"));
    }
}
