package com.bif.server.features.place.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlaceIdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceIdentityService.class);

    private final PlaceMappingRepository placeMappingRepository;
    private final PlaceRepository placeRepository;
    private final MongoTemplate mongoTemplate;

    public PlaceIdentityService(PlaceMappingRepository placeMappingRepository,
                                PlaceRepository placeRepository,
                                MongoTemplate mongoTemplate) {
        this.placeMappingRepository = placeMappingRepository;
        this.placeRepository = placeRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Synchronized to ensure thread-safety when creating new mappings
     * for the same location/external ids concurrently. 
     */
    @Transactional
    public synchronized String resolveInternalPlaceId(String source, String extId, double lat, double lng, String name) {
        LOGGER.info("Resolving internalPlaceId for externalId: {}, name: {}, coordinates: [{}, {}]", extId, name, lat, lng);

        // 1. Check exact match
        Optional<PlaceMapping> exactMatch = placeMappingRepository.findByExternalSourceAndExternalId(source, extId);
        if (exactMatch.isPresent()) {
            String internalId = exactMatch.get().getInternalPlaceId();
            LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Exact Match)", internalId, extId);
            return internalId;
        }

        // 2. Search within 30m radius
        Point point = new Point(lng, lat);
        Query geoQuery = new Query(Criteria.where("location").withinSphere(
                new org.springframework.data.geo.Circle(point, new Distance(30.0 / 1000.0, Metrics.KILOMETERS))
        ));

        List<PlaceMapping> nearbyMappings = mongoTemplate.find(geoQuery, PlaceMapping.class);

        // Name similarity > 80%
        for (PlaceMapping mapping : nearbyMappings) {
            if (isNameSimilar(mapping.getName(), name)) {
                // 3. Match found - use existing internalPlaceId
                String internalId = mapping.getInternalPlaceId();
                createMapping(internalId, source, extId, lat, lng, name);
                LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Spatial Match)", internalId, extId);
                return internalId;
            }
        }

        // 4. No match, generate new UUID and Place
        String newInternalId = UUID.randomUUID().toString();

        Place newPlace = new Place();
        newPlace.setId(newInternalId);
        newPlace.setName(name != null ? name : "Unknown Place");
        newPlace.setLocation(new Location(lat, lng));
        newPlace.setAddress("");
        newPlace.setPlaceSource(source);
        newPlace.setPersistedByAction("resolved");
        placeRepository.save(newPlace);

        createMapping(newInternalId, source, extId, lat, lng, name);
        LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (New ID Generated)", newInternalId, extId);
        return newInternalId;
    }

    private String createMapping(String internalId, String source, String extId, double lat, double lng, String name) {
        PlaceMapping newMapping = new PlaceMapping();
        newMapping.setInternalPlaceId(internalId);
        newMapping.setExternalSource(source);
        newMapping.setExternalId(extId);
        newMapping.setName(name);
        newMapping.setLocation(new GeoJsonPoint(lng, lat));
        newMapping.setCreatedAt(Instant.now());
        placeMappingRepository.save(newMapping);
        return internalId;
    }

    private boolean isNameSimilar(String name1, String name2) {
        if (name1 == null || name2 == null) return false;
        double similarity = calculateSimilarity(name1.toLowerCase(), name2.toLowerCase());
        return similarity > 0.8;
    }

    private double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2; shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
