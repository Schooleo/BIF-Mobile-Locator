package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.config.TypesenseProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypesensePlaceBootstrapIndexerTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceSearchIndexSyncService placeSearchIndexSyncService;

    @Test
    void run_SkipsWhenTypesenseDisabled() throws Exception {
        TypesenseProperties properties = new TypesenseProperties();
        properties.setEnabled(false);
        properties.setBootstrapReindexOnStartup(true);

        TypesensePlaceBootstrapIndexer indexer = new TypesensePlaceBootstrapIndexer(
                properties,
                placeRepository,
                placeSearchIndexSyncService);

        indexer.run(null);

        verify(placeRepository, never()).findByDeletedFalse();
        verify(placeSearchIndexSyncService, never()).batchUpsert(anyList());
    }

    @Test
    void run_SkipsWhenBootstrapFlagDisabled() throws Exception {
        TypesenseProperties properties = new TypesenseProperties();
        properties.setEnabled(true);
        properties.setBootstrapReindexOnStartup(false);

        TypesensePlaceBootstrapIndexer indexer = new TypesensePlaceBootstrapIndexer(
                properties,
                placeRepository,
                placeSearchIndexSyncService);

        indexer.run(null);

        verify(placeRepository, never()).findByDeletedFalse();
        verify(placeSearchIndexSyncService, never()).batchUpsert(anyList());
    }

    @Test
    void run_IndexesAllNonDeletedPlacesWhenEnabled() throws Exception {
        TypesenseProperties properties = new TypesenseProperties();
        properties.setEnabled(true);
        properties.setBootstrapReindexOnStartup(true);

        Place p1 = new Place();
        p1.setId("p1");
        Place p2 = new Place();
        p2.setId("p2");

        List<Place> places = List.of(p1, p2);
        when(placeRepository.findByDeletedFalse()).thenReturn(places);
        when(placeSearchIndexSyncService.batchUpsert(places)).thenReturn(2);

        TypesensePlaceBootstrapIndexer indexer = new TypesensePlaceBootstrapIndexer(
                properties,
                placeRepository,
                placeSearchIndexSyncService);

        indexer.run(null);

        verify(placeSearchIndexSyncService).ensureCollectionExists();
        verify(placeRepository).findByDeletedFalse();
        verify(placeSearchIndexSyncService).batchUpsert(places);
    }
}

