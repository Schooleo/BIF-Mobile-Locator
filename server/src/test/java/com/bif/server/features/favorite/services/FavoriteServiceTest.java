package com.bif.server.features.favorite.services;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
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

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(favoriteRepository);
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
}
