package com.bif.server.features.sync.services;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteSyncEntityHandlerTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PlaceIdentityService placeIdentityService;

    private FavoriteSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FavoriteSyncEntityHandler(favoriteRepository, placeIdentityService);
    }

    @Test
    void entityType_ReturnsFavorite() {
        assertEquals("favorite", handler.entityType());
    }

    @Test
    void applyPushedChange_WhenDelete_SoftDeletesFavorite() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("fav-1");
        pushed.setOperation("DELETE");
        pushed.setPayload("{\"id\":\"fav-1\"}");

        Favorite existing = new Favorite();
        existing.setId("fav-1");
        existing.setUserId("user-1");
        existing.setDeleted(false);

        when(favoriteRepository.findByIdAndUserId("fav-1", "user-1")).thenReturn(Optional.of(existing));

        String resultPayload = handler.applyPushedChange(pushed, "user-1", 10L);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(captor.capture());
        Favorite saved = captor.getValue();

        assertTrue(saved.isDeleted());
        assertEquals(10L, saved.getServerVersion());
        assertEquals("user-1", saved.getLastModifiedBy());
        assertTrue(resultPayload.contains("\"deleted\":true"));
        assertTrue(resultPayload.contains("\"serverVersion\":10"));
    }

    @Test
    void applyPushedChange_WhenUpdate_UpdatesFavorite() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("fav-2");
        pushed.setOperation("UPDATE");
        String json = "{\"id\":\"fav-2\",\"name\":\"New Name\",\"latitude\":10.0,\"longitude\":20.0,\"rating\":5}";
        pushed.setPayload(json);

        Favorite existing = new Favorite();
        existing.setId("fav-2");
        existing.setUserId("user-1");
        
        when(favoriteRepository.findByIdAndUserId("fav-2", "user-1")).thenReturn(Optional.of(existing));

        String resultPayload = handler.applyPushedChange(pushed, "user-1", 15L);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(captor.capture());
        Favorite saved = captor.getValue();

        assertEquals("New Name", saved.getName());
        assertEquals(10.0, saved.getLocation().getLatitude());
        assertEquals(20.0, saved.getLocation().getLongitude());
        assertEquals(5, saved.getRating());
        assertEquals(15L, saved.getServerVersion());
        assertEquals("user-1", saved.getLastModifiedBy());
        assertTrue(resultPayload.contains("\"serverVersion\":15"));
        assertTrue(resultPayload.contains("\"name\":\"New Name\""));
    }

    @Test
    void applyPushedChange_WhenCreate_CreatesNewFavorite() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("fav-new");
        pushed.setOperation("CREATE");
        String json = "{\"id\":\"fav-new\",\"name\":\"Created\"}";
        pushed.setPayload(json);

        when(favoriteRepository.findByIdAndUserId("fav-new", "user-1")).thenReturn(Optional.empty());

        handler.applyPushedChange(pushed, "user-1", 20L);

        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(captor.capture());
        Favorite saved = captor.getValue();

        assertEquals("fav-new", saved.getId());
        assertEquals("Created", saved.getName());
        assertEquals("user-1", saved.getUserId());
        assertEquals(20L, saved.getServerVersion());
    }

    @Test
    void resolvePayload_WhenMissing_FetchesFromDatabase() {
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityId("fav-3");
        entry.setUserId("user-2");
        entry.setServerVersion(5L);

        Favorite existing = new Favorite();
        existing.setId("fav-3");
        existing.setName("Existing");
        existing.setUserId("user-2");

        when(favoriteRepository.findByIdAndUserId("fav-3", "user-2")).thenReturn(Optional.of(existing));

        String resolved = handler.resolvePayload(entry);

        assertNotNull(resolved);
        assertTrue(resolved.contains("\"name\":\"Existing\""));
        assertTrue(resolved.contains("\"serverVersion\":5"));
    }
}
