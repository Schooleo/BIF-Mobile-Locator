package com.bif.server.features.favorite.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private PlaceRepository placeRepository;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository, placeIdentityService, placeRepository);
        lenient().when(placeRepository.findById(anyString())).thenReturn(Optional.empty());
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
        item.setExternalSource("GOOGLE_MAPS");
        item.setExternalId("gm-0");
        item.setPlaceName("Coffee");
        item.setLocation(new Location(10.0, 20.0));
        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-0", 10.0, 20.0, "Coffee"))
                .thenReturn("place-0");
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Favorite result = favoriteService.save(item);

        assertEquals("place-0", result.getPlaceId());
        verify(favoriteRepository).save(any(Favorite.class));
    }

    @Test
    void save_WhenPlaceIdMissing_ResolvesBeforeSaving() {
        Favorite item = new Favorite();
        item.setId("f1");
        item.setUserId("u1");
        item.setName("Cafe");
        item.setPlaceName("Cafe");
        item.setExternalSource("GOOGLE_MAPS");
        item.setExternalId("gm-1");
        item.setLocation(new Location(10.0, 20.0));

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-1", 10.0, 20.0, "Cafe"))
                .thenReturn("place-123");
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Favorite result = favoriteService.save(item);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(placeIdentityService).resolveInternalPlaceId("GOOGLE_MAPS", "gm-1", 10.0, 20.0, "Cafe");
        verify(favoriteRepository).save(captor.capture());
        assertEquals("place-123", captor.getValue().getPlaceId());
        assertEquals("place-123", result.getPlaceId());
    }

    @Test
    void save_WhenPlaceIdExists_StillResolvesAndOverwritesCanonicalId() {
        Favorite item = new Favorite();
        item.setId("f2");
        item.setUserId("u1");
        item.setPlaceId("place-xyz");
        item.setExternalSource("GOOGLE_MAPS");
        item.setExternalId("gm-2");
        item.setPlaceName("Cafe");
        item.setName("Cafe");
        item.setLocation(new Location(10.0, 20.0));

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-2", 10.0, 20.0, "Cafe"))
                .thenReturn("place-canonical");
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Favorite result = favoriteService.save(item);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(placeIdentityService).resolveInternalPlaceId("GOOGLE_MAPS", "gm-2", 10.0, 20.0, "Cafe");
        verify(favoriteRepository).save(captor.capture());
        assertEquals("place-canonical", captor.getValue().getPlaceId());
        assertEquals("place-canonical", result.getPlaceId());
    }

    @Test
    void save_WhenResolutionFails_ThrowsIllegalStateException() {
        Favorite item = new Favorite();
        item.setId("f3");
        item.setUserId("u1");
        item.setName("Cafe");
        item.setExternalSource("GOOGLE_MAPS");
        item.setExternalId("gm-3");
        item.setLocation(new Location(10.0, 20.0));

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-3", 10.0, 20.0, "Cafe"))
                .thenThrow(new RuntimeException("boom"));

        assertThrows(IllegalStateException.class, () -> favoriteService.save(item));
        verify(favoriteRepository, never()).save(any(Favorite.class));
    }

    @Test
    void save_WhenIdentityMetadataMissing_ThrowsIllegalArgumentException() {
        Favorite item = new Favorite();
        item.setId("f4");
        item.setUserId("u1");
        item.setName("Cafe");
        item.setLocation(new Location(10.0, 20.0));

        assertThrows(IllegalArgumentException.class, () -> favoriteService.save(item));
        verify(placeIdentityService, never()).resolveInternalPlaceId(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
        verify(favoriteRepository, never()).save(any(Favorite.class));
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
        input.setExternalSource("GOOGLE_MAPS");
        input.setExternalId("gm-5");
        input.setPlaceName("Coffee");
        input.setLocation(new Location(10.0, 20.0));

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-5", 10.0, 20.0, "Coffee"))
                .thenReturn("place-5");
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Favorite result = favoriteService.saveMyFavorite("u1", input);

        assertSame(input, result);
        assertEquals("u1", input.getUserId());
        assertEquals("place-5", input.getPlaceId());
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
