package com.bif.server.features.place.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlaceIdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceIdentityService.class);
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();

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

    public String resolveInternalPlaceId(String source, String extId, double lat, double lng, String name) {
        LOGGER.info("Resolving internalPlaceId for externalId: {}, name: {}, coordinates: [{}, {}]", extId, name, lat, lng);

        // 1. Check exact match
        Optional<PlaceMapping> existingMapping = placeMappingRepository.findByExternalSourceAndExternalId(source, extId);
        if (existingMapping.isPresent()) {
            String internalId = existingMapping.get().getInternalPlaceId();
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
                try {
                    createMapping(internalId, source, extId, lat, lng, name);
                    LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Spatial Match)", internalId, extId);
                    return internalId;
                } catch (DataIntegrityViolationException e) {
                    Optional<PlaceMapping> concurrentMapping = placeMappingRepository
                            .findByExternalSourceAndExternalId(source, extId);
                    if (concurrentMapping.isPresent()) {
                        String resolvedId = concurrentMapping.get().getInternalPlaceId();
                        LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Spatial Race Reused)",
                                resolvedId,
                                extId);
                        return resolvedId;
                    }
                    throw e;
                }
            }
        }

        // Re-check to avoid duplicate place creation under concurrent requests.
        existingMapping = placeMappingRepository.findByExternalSourceAndExternalId(source, extId);
        if (existingMapping.isPresent()) {
            String internalId = existingMapping.get().getInternalPlaceId();
            LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Concurrent Exact Match)", internalId, extId);
            return internalId;
        }

        // 4. No match, generate new UUID and atomically create Place + mapping.
        String newInternalId = UUID.randomUUID().toString();

        Place newPlace = new Place();
        newPlace.setId(newInternalId);
        newPlace.setName(name != null ? name : "Unknown Place");
        newPlace.setLocation(new Location(lat, lng));
        newPlace.setAddress("");
        newPlace.setPlaceSource(source);
        newPlace.setPersistedByAction("resolved");
        placeRepository.save(newPlace);

        try {
            createMapping(newInternalId, source, extId, lat, lng, name);
            LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (New ID Generated)", newInternalId, extId);
            return newInternalId;
        } catch (DataIntegrityViolationException e) {
            try {
                placeRepository.deleteById(newInternalId);
            } catch (Exception cleanupEx) {
                LOGGER.warn("Failed to cleanup orphan Place id={} after mapping conflict", newInternalId, cleanupEx);
            }

            Optional<PlaceMapping> concurrentMapping = placeMappingRepository
                    .findByExternalSourceAndExternalId(source, extId);
            if (concurrentMapping.isPresent()) {
                String resolvedId = concurrentMapping.get().getInternalPlaceId();
                LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Concurrent Mapping Reused)",
                        resolvedId,
                        extId);
                return resolvedId;
            }
            throw e;
        }
    }

    private void createMapping(String internalId, String source, String extId, double lat, double lng, String name) {
        PlaceMapping newMapping = new PlaceMapping();
        newMapping.setInternalPlaceId(internalId);
        newMapping.setExternalSource(source);
        newMapping.setExternalId(extId);
        newMapping.setName(name);
        newMapping.setLocation(new GeoJsonPoint(lng, lat));
        newMapping.setCreatedAt(Instant.now());
        placeMappingRepository.save(newMapping);
    }

    private boolean isNameSimilar(String name1, String name2) {
        if (name1 == null || name2 == null) {return false;}
        double similarity = calculateSimilarity(name1.toLowerCase(), name2.toLowerCase());
        return similarity > 0.8;
    }

    private double calculateSimilarity(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = LEVENSHTEIN.apply(s1, s2);
        return (maxLength - distance) / (double) maxLength;
    }
}
