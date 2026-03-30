package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.config.TypesenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TypesensePlaceBootstrapIndexer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TypesensePlaceBootstrapIndexer.class);

    private final TypesenseProperties typesenseProperties;
    private final PlaceRepository placeRepository;
    private final PlaceSearchIndexSyncService placeSearchIndexSyncService;

    public TypesensePlaceBootstrapIndexer(
            TypesenseProperties typesenseProperties,
            PlaceRepository placeRepository,
            PlaceSearchIndexSyncService placeSearchIndexSyncService) {
        this.typesenseProperties = typesenseProperties;
        this.placeRepository = placeRepository;
        this.placeSearchIndexSyncService = placeSearchIndexSyncService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!typesenseProperties.isEnabled()) {
            LOGGER.info("Skipping bootstrap reindex: typesense.enabled=false");
            return;
        }

        if (!typesenseProperties.isBootstrapReindexOnStartup()) {
            LOGGER.info("Skipping bootstrap reindex: bootstrap flag disabled");
            return;
        }

        List<Place> places = placeRepository.findByDeletedFalse();
        if (places.isEmpty()) {
            LOGGER.info("Bootstrap reindex: no places to index");
            return;
        }

        int indexedCount = 0;
        for (Place place : places) {
            if (place == null || place.getId() == null || place.getId().isBlank()) {
                continue;
            }
            try {
                placeSearchIndexSyncService.upsert(place);
                indexedCount++;
            } catch (RuntimeException ex) {
                LOGGER.error("Bootstrap reindex failed for place {}", place.getId(), ex);
            }
        }

        LOGGER.info("Bootstrap reindex completed. Indexed {} place(s)", indexedCount);
    }
}
