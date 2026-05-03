package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.dto.graphql.DeleteMyFavoriteResult;
import com.bif.server.features.favorite.dto.graphql.UpsertFavoriteInput;
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
        UpsertFavoriteInput input = new UpsertFavoriteInput(
            "f1",
            "GOOGLE_MAPS",
            "gm-1",
            "Coffee",
            "Coffee",
            null,
            "Address",
            "Desc",
            "Note",
            5,
            null,
            "u1");
        Favorite saved = new Favorite();
        saved.setId("f1");
        when(favoriteService.save(any(Favorite.class))).thenReturn(saved);

        Favorite result = controller.upsertFavorite(input);

        assertSame(saved, result);
        verify(favoriteService).save(any(Favorite.class));
    }

    @Test
    void deleteFavorite_DelegatesToService() {
        when(favoriteService.deleteById("f1")).thenReturn(true);

        assertTrue(controller.deleteFavorite("f1"));
    }

    @Test
    void myFavorites_WhenUserIdMissing_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> controller.myFavorites(" "));
        verify(favoriteService, never()).getMyFavorites(anyString());
    }

    @Test
    void myFavorites_WhenAuthorized_ReturnsCurrentUserData() {
        Favorite item = new Favorite();
        when(favoriteService.getMyFavorites("u1")).thenReturn(List.of(item));

        List<Favorite> result = controller.myFavorites("u1");

        assertEquals(1, result.size());
        verify(favoriteService).getMyFavorites("u1");
    }

    @Test
    void upsertMyFavorite_DelegatesToService() {
        UpsertFavoriteInput input = new UpsertFavoriteInput(
                "f1",
                "GOOGLE_MAPS",
                "gm-1",
                "Coffee",
                "Coffee",
                null,
                "Address",
                "Desc",
                "Note",
                5,
                null,
                null);
        Favorite saved = new Favorite();
        saved.setId("f1");
        when(favoriteService.saveMyFavorite(eq("u1"), any(Favorite.class))).thenReturn(saved);

        Favorite result = controller.upsertMyFavorite("u1", input);

        assertSame(saved, result);
        verify(favoriteService).saveMyFavorite(eq("u1"), any(Favorite.class));
    }

    @Test
    void deleteMyFavorite_WhenForbidden_ReturnsForbiddenEnum() {
        when(favoriteService.deleteMyFavorite("u1", "f1"))
                .thenReturn(FavoriteService.DeleteMyFavoriteResult.FORBIDDEN);

        DeleteMyFavoriteResult result = controller.deleteMyFavorite("u1", "f1");

        assertEquals(DeleteMyFavoriteResult.FORBIDDEN, result);
    }
}
