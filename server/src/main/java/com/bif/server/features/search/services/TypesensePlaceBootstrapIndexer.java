package com.bif.server.features.search.services;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.config.TypesenseProperties;

@Component
@Order(2) // RUNS AFTER MONGODB BOOTSTRAP
public class TypesensePlaceBootstrapIndexer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypesensePlaceBootstrapIndexer.class);

    private static final int BATCH_SIZE = 500;

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

        LOGGER.info("Starting Typesense Bootstrap Reindex...");

        // Ensure the collection schema exists before upserting documents
        placeSearchIndexSyncService.ensureCollectionExists();

        // Return newly inserted Overture places
        List<Place> places = placeRepository.findByDeletedFalse();
        if (places.isEmpty()) {
            LOGGER.info("Bootstrap reindex: no places to index");
            return;
        }

        // Batch import for efficiency — avoids overwhelming Typesense with individual requests
        int totalIndexed = 0;
        for (int i = 0; i < places.size(); i += BATCH_SIZE) {
            List<Place> batch = places.subList(i, Math.min(i + BATCH_SIZE, places.size()));

            // Filter out invalid entries
            List<Place> validBatch = new ArrayList<>();
            for (Place place : batch) {
                if (place != null && place.getId() != null && !place.getId().isBlank()) {
                    validBatch.add(place);
                }
            }

            if (!validBatch.isEmpty()) {
                int imported = placeSearchIndexSyncService.batchUpsert(validBatch);
                totalIndexed += imported;
                LOGGER.info("Bootstrap reindex progress: imported {}/{} places",
                        totalIndexed, places.size());
            }
        }

        LOGGER.info("✅ Bootstrap reindex completed. Indexed {} place(s) into Typesense", totalIndexed);
    }
}