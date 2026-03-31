package com.bif.server.features.place.services;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Order(1) // RUNS BEFORE TYPESENSE INDEXER
@RequiredArgsConstructor
public class PlaceBootstrapService implements ApplicationRunner {

    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper;
    /*  */
    // Points to the Docker volume mapped in docker-compose.yml
    @Value("${app.maps-data.places-file:/map-data/places.geojson}")
    private String placesFilePath;

    private static final int BATCH_SIZE = 1000;

    @Override
    public void run(ApplicationArguments args) {
        // 1. Check if DB is already populated to avoid re-running on every restart
        if (placeRepository.count() > 1000) {
            log.info("✅ Places database is already populated. Skipping MongoDB bootstrap.");
            return;
        }

        File file = new File(placesFilePath);
        if (!file.exists()) {
            log.warn("🗺️ Overture places file not found at {}. Skipping bootstrap.", placesFilePath);
            return;
        }

        log.info("🚀 Starting Streaming Import of Overture Places to MongoDB...");
        long startTime = System.currentTimeMillis();

        try (JsonParser parser = new JsonFactory().createParser(file)) {
            parser.setCodec(objectMapper);

            // Fast-forward to the "features" array in the GeoJSON
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if ("features".equals(parser.currentName())) {
                    parser.nextToken(); // Move to '['
                    break;
                }
            }

            List<Place> batch = new ArrayList<>();
            int totalImported = 0;

            // Read each feature one by one
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                JsonNode featureNode = parser.readValueAsTree();
                Place place = parseOvertureFeature(featureNode);

                if (place != null) {
                    batch.add(place);
                }

                if (batch.size() >= BATCH_SIZE) {
                    placeRepository.saveAll(batch);
                    totalImported += batch.size();
                    log.info("Imported {} places into MongoDB...", totalImported);
                    batch.clear();
                }
            }

            // Save the final remaining items
            if (!batch.isEmpty()) {
                placeRepository.saveAll(batch);
                totalImported += batch.size();
            }

            log.info("🎉 Successfully imported {} places into MongoDB in {} ms!",
                    totalImported, (System.currentTimeMillis() - startTime));

        } catch (Exception e) {
            log.error("❌ Failed to parse and bootstrap places.geojson", e);
        }
    }

    private Place parseOvertureFeature(JsonNode featureNode) {
        try {
            JsonNode properties = featureNode.path("properties");
            JsonNode geometry = featureNode.path("geometry");
            JsonNode coordinates = geometry.path("coordinates");

            if (properties.isMissingNode() || coordinates.isMissingNode()) {
                return null;
            }

            String name = properties.path("names").path("primary").asText("");
            if (name.isBlank()) {
                return null;
            }

            String category = properties.path("categories").path("main").asText("Unknown");

            // GeoJSON coordinates are ALWAYS [Longitude, Latitude]
            double lng = coordinates.get(0).asDouble();
            double lat = coordinates.get(1).asDouble();

            Location location = new Location();
            location.setLatitude(lat);
            location.setLongitude(lng);

            Place place = new Place();
            place.setId(UUID.randomUUID().toString()); // Set ID for Typesense sync tracking
            place.setName(name);
            place.setLocation(location);
            place.setTags(Collections.singletonList(category));
            place.setPlaceSource("OVERTURE_MAPS");
            place.setPersistedByAction("SYSTEM_BOOTSTRAP");

            // Try to extract an address if Overture provided it
            JsonNode addresses = properties.path("addresses");
            if (addresses.isArray() && !addresses.isEmpty()) {
                place.setAddress(addresses.get(0).path("freeform").asText(""));
            }

            return place;

        } catch (Exception e) {
            log.warn("Failed to parse a place feature: {}", e.getMessage());
            return null;
        }
    }
}