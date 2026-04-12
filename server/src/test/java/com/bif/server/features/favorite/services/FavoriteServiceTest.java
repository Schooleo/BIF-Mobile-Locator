package com.bif.server.features.favorite.services;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
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
class FavoriteServiceTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PlaceIdentityService placeIdentityService;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, placeIdentityService);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        Favorite item = new Favorite();
        when(favoriteRepository.findAll()).thenReturn(List.of(item));

        List<Favorite> result = favoriteService.getAll();

        assertEquals(1, result.size());
        verify(favoriteRepository).findAll();
    }

    @Test
    void getByUserId_ReturnsRepositoryData() {
        Favorite item = new Favorite();
        when(favoriteRepository.findByUserId("u1")).thenReturn(List.of(item));

        List<Favorite> result = favoriteService.getByUserId("u1");

        assertEquals(1, result.size());
        verify(favoriteRepository).findByUserId("u1");
    }

    @Test
    void getById_ReturnsOptional() {
        Favorite item = new Favorite();
        when(favoriteRepository.findById("f1")).thenReturn(Optional.of(item));

        Optional<Favorite> result = favoriteService.getById("f1");

        assertTrue(result.isPresent());
        verify(favoriteRepository).findById("f1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        Favorite item = new Favorite();
        when(favoriteRepository.save(item)).thenReturn(item);

        Favorite result = favoriteService.save(item);

        assertSame(item, result);
        verify(favoriteRepository).save(item);
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(favoriteRepository.existsById("f1")).thenReturn(true);

        boolean result = favoriteService.deleteById("f1");

        assertTrue(result);
        verify(favoriteRepository).deleteById("f1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(favoriteRepository.existsById("f1")).thenReturn(false);

        boolean result = favoriteService.deleteById("f1");

        assertFalse(result);
        verify(favoriteRepository, never()).deleteById(anyString());
    }

    @Test
    void getMyFavorites_WhenUserIdBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> favoriteService.getMyFavorites(" "));
        verify(favoriteRepository, never())
            .findByUserIdAndDeletedFalse(anyString());
        }

        @Test
        void getMyFavorites_FiltersOutDeletedFavorites() {
        Favorite item = new Favorite();
        when(favoriteRepository.findByUserIdAndDeletedFalse("u1"))
            .thenReturn(List.of(item));

        List<Favorite> result = favoriteService.getMyFavorites("u1");

        assertEquals(1, result.size());
        verify(favoriteRepository).findByUserIdAndDeletedFalse("u1");
        }

        @Test
        void getMyFavoriteById_UsesNonDeletedLookup() {
        Favorite item = new Favorite();
        when(favoriteRepository.findByIdAndUserIdAndDeletedFalse("f1", "u1"))
            .thenReturn(Optional.of(item));

        Optional<Favorite> result = favoriteService.getMyFavoriteById("u1",
            "f1");

        assertTrue(result.isPresent());
        verify(favoriteRepository).findByIdAndUserIdAndDeletedFalse("f1", "u1");
    }

    @Test
    void saveMyFavorite_WhenCreate_OverwritesUserIdWithCurrentUser() {
        Favorite input = new Favorite();
        input.setUserId("other-user");
        when(favoriteRepository.save(input)).thenReturn(input);

        Favorite result = favoriteService.saveMyFavorite("u1", input);

        assertSame(input, result);
        assertEquals("u1", input.getUserId());
        verify(favoriteRepository).save(input);
    }

    @Test
    void saveMyFavorite_WhenNotOwner_ThrowsSecurityException() {
        Favorite input = new Favorite();
        input.setId("f1");

        Favorite existing = new Favorite();
        existing.setId("f1");
        existing.setUserId("u2");
        when(favoriteRepository.findById("f1")).thenReturn(Optional.of(existing));

        assertThrows(SecurityException.class, () -> favoriteService.saveMyFavorite("u1", input));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    void deleteMyFavorite_WhenOwner_DeletesAndReturnsDeleted() {
        when(favoriteRepository.existsByIdAndUserId("f1", "u1")).thenReturn(true);

        FavoriteService.DeleteMyFavoriteResult result = favoriteService.deleteMyFavorite("u1", "f1");

        assertEquals(FavoriteService.DeleteMyFavoriteResult.DELETED, result);
        verify(favoriteRepository).deleteById("f1");
    }

    @Test
    void deleteMyFavorite_WhenExistsButNotOwner_ReturnsForbidden() {
        when(favoriteRepository.existsByIdAndUserId("f1", "u1")).thenReturn(false);
        when(favoriteRepository.existsById("f1")).thenReturn(true);

        FavoriteService.DeleteMyFavoriteResult result = favoriteService.deleteMyFavorite("u1", "f1");

        assertEquals(FavoriteService.DeleteMyFavoriteResult.FORBIDDEN, result);
        verify(favoriteRepository, never()).deleteById(anyString());
    }
}
