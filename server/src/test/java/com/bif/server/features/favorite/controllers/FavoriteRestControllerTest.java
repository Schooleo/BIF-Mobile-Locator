package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.dto.rest.FavoriteResponse;
import com.bif.server.features.favorite.dto.rest.UpsertMyFavoriteRequest;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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

    @Test
    void getMyFavorites_WhenHeaderMissing_ReturnsUnauthorized() {
        ResponseEntity<List<FavoriteResponse>> result = controller.getMyFavorites(null);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(favoriteService, never()).getMyFavorites(anyString());
    }

    @Test
    void getMyFavorites_WhenAuthorized_ReturnsCurrentUserFavorites() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        Favorite item = new Favorite();
        item.setId("f1");
        item.setName("Coffee");

        when(favoriteService.getMyFavorites("u1")).thenReturn(List.of(item));

        ResponseEntity<List<FavoriteResponse>> result = controller.getMyFavorites(auth);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals("f1", result.getBody().getFirst().id());
        verify(favoriteService).getMyFavorites("u1");
    }

    @Test
    void getMyFavoriteById_WhenMissing_ReturnsNotFound() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        when(favoriteService.getMyFavoriteById("u1", "f1")).thenReturn(Optional.empty());

        ResponseEntity<FavoriteResponse> result = controller.getMyFavoriteById(auth, "f1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        verify(favoriteService).getMyFavoriteById("u1", "f1");
    }

    @Test
    void upsertMyFavorite_WhenHeaderMissing_ReturnsUnauthorized() {
        UpsertMyFavoriteRequest request = new UpsertMyFavoriteRequest(null, null, "GOOGLE_MAPS", "gm-0", "Coffee", "Coffee", null, null, null, null, 5, null);

        ResponseEntity<FavoriteResponse> result = controller.upsertMyFavorite(null, request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(favoriteService, never()).saveMyFavorite(anyString(), any(Favorite.class));
    }

    @Test
    void upsertMyFavorite_WhenNotOwner_ReturnsForbidden() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        UpsertMyFavoriteRequest request = new UpsertMyFavoriteRequest("f1", null, "GOOGLE_MAPS", "gm-1", "Coffee", "Coffee", null, null, null, null, 5, null);

        when(favoriteService.saveMyFavorite(eq("u1"), any(Favorite.class))).thenThrow(new SecurityException("forbidden"));

        ResponseEntity<FavoriteResponse> result = controller.upsertMyFavorite(auth, request);

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteService).saveMyFavorite(eq("u1"), captor.capture());
        assertNull(captor.getValue().getPlaceId());
        assertEquals("GOOGLE_MAPS", captor.getValue().getExternalSource());
    }

    @Test
    void upsertMyFavorite_WhenTargetMissing_ReturnsNotFound() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        UpsertMyFavoriteRequest request = new UpsertMyFavoriteRequest("f1", null, "GOOGLE_MAPS", "gm-1", "Coffee", "Coffee", null, null, null, null, 5, null);

        when(favoriteService.saveMyFavorite(eq("u1"), any(Favorite.class)))
                .thenThrow(new NoSuchElementException("missing"));

        ResponseEntity<FavoriteResponse> result = controller.upsertMyFavorite(auth, request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteService).saveMyFavorite(eq("u1"), captor.capture());
        assertNull(captor.getValue().getPlaceId());
    }

    @Test
    void upsertMyFavorite_WhenAuthorized_MapsIdentitySeedWithoutClientPlaceId() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        UpsertMyFavoriteRequest request = new UpsertMyFavoriteRequest("f1", null, "GOOGLE_MAPS", "gm-1", "Coffee", "Coffee", null, null, null, null, 5, null);
        Favorite saved = new Favorite();
        saved.setId("f1");
        saved.setPlaceId("place-123");
        saved.setExternalSource("GOOGLE_MAPS");
        saved.setExternalId("gm-1");
        saved.setPlaceName("Coffee");
        when(favoriteService.saveMyFavorite(eq("u1"), any(Favorite.class))).thenReturn(saved);

        ResponseEntity<FavoriteResponse> result = controller.upsertMyFavorite(auth, request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteService).saveMyFavorite(eq("u1"), captor.capture());
        assertNull(captor.getValue().getPlaceId());
        assertEquals("GOOGLE_MAPS", captor.getValue().getExternalSource());
    }

    @Test
    void deleteMyFavorite_WhenOwner_ReturnsNoContent() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        when(favoriteService.deleteMyFavorite("u1", "f1")).thenReturn(FavoriteService.DeleteMyFavoriteResult.DELETED);

        ResponseEntity<Void> result = controller.deleteMyFavorite(auth, "f1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteMyFavorite_WhenNotOwner_ReturnsForbidden() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        when(favoriteService.deleteMyFavorite("u1", "f1")).thenReturn(FavoriteService.DeleteMyFavoriteResult.FORBIDDEN);

        ResponseEntity<Void> result = controller.deleteMyFavorite(auth, "f1");

        assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
    }

    @Test
    void deleteMyFavorite_WhenMissing_ReturnsNotFound() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1", null);
        when(favoriteService.deleteMyFavorite("u1", "f1")).thenReturn(FavoriteService.DeleteMyFavoriteResult.NOT_FOUND);

        ResponseEntity<Void> result = controller.deleteMyFavorite(auth, "f1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
