package com.bif.server.common.migration;

import com.bif.server.common.migration.model.SchemaMigration;
import com.bif.server.common.migration.repository.SchemaMigrationRepository;
import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteContractV2MigrationRunnerTest {

    @Mock
    private FavoriteRepository favoriteRepository;

    @Mock
    private PlaceIdentityService placeIdentityService;

    @Mock
    private SchemaMigrationRepository schemaMigrationRepository;

    private FavoriteContractV2MigrationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new FavoriteContractV2MigrationRunner(
                favoriteRepository,
                placeIdentityService,
                schemaMigrationRepository);
        ReflectionTestUtils.setField(runner, "migrationEnabled", true);
    }

    @Test
    void run_WhenMarkerExists_SkipsMigration() throws Exception {
        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(true);

        runner.run(null);

        verify(favoriteRepository, never()).findAll();
        verify(schemaMigrationRepository, never()).save(any(SchemaMigration.class));
    }

    @Test
    void run_WhenValidFavorite_ResolvesAndPersistsCanonicalPlaceId() throws Exception {
        Favorite favorite = new Favorite();
        favorite.setId("f1");
        favorite.setUserId("u1");
        favorite.setExternalSource("GOOGLE_MAPS");
        favorite.setExternalId("gm-1");
        favorite.setPlaceName("Coffee");
        favorite.setLocation(new Location(10.0, 20.0));
        favorite.setPlaceId("legacy-id");

        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(false);
        when(favoriteRepository.findAll()).thenReturn(List.of(favorite));
        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-1", 10.0, 20.0, "Coffee"))
                .thenReturn("canonical-1");

        runner.run(null);

        ArgumentCaptor<Favorite> favoriteCaptor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(favoriteCaptor.capture());
        assertEquals("canonical-1", favoriteCaptor.getValue().getPlaceId());
        assertEquals(null, favoriteCaptor.getValue().getExternalId());
        assertEquals(null, favoriteCaptor.getValue().getPlaceName());

        ArgumentCaptor<SchemaMigration> migrationCaptor = ArgumentCaptor.forClass(SchemaMigration.class);
        verify(schemaMigrationRepository).save(migrationCaptor.capture());
        assertEquals("favorite-contract-v2", migrationCaptor.getValue().getId());
    }

    @Test
    void run_WhenFavoriteSeedInvalid_SkipsFavoriteAndStillWritesMarker() throws Exception {
        Favorite invalid = new Favorite();
        invalid.setId("f-bad");
        invalid.setUserId("u1");
        invalid.setExternalSource("GOOGLE_MAPS");
        invalid.setExternalId("gm-bad");
        invalid.setPlaceName("No location");

        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(false);
        when(favoriteRepository.findAll()).thenReturn(List.of(invalid));

        runner.run(null);

        verify(placeIdentityService, never()).resolveInternalPlaceId(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
        ArgumentCaptor<Favorite> favoriteCaptor = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(favoriteCaptor.capture());
        assertEquals(null, favoriteCaptor.getValue().getExternalId());
        assertEquals(null, favoriteCaptor.getValue().getPlaceName());
        verify(schemaMigrationRepository).save(any(SchemaMigration.class));
    }
}
