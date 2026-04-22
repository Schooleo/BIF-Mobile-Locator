package com.bif.server.features.place.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceIdentityLock;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class PlaceIdentityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceIdentityService.class);
    private static final LevenshteinDistance LEVENSHTEIN = new LevenshteinDistance();
    private static final int LOCK_RETRY_ATTEMPTS = 8;
    private static final long LOCK_RETRY_MILLIS = 40L;
    private static final int LOCK_COORDINATE_SCALE = 10_000;

    private final PlaceMappingRepository placeMappingRepository;
    private final MongoTemplate mongoTemplate;
    private final PlaceCleanupService placeCleanupService;

    public PlaceIdentityService(PlaceMappingRepository placeMappingRepository,
                                MongoTemplate mongoTemplate,
                                PlaceCleanupService placeCleanupService) {
        this.placeMappingRepository = placeMappingRepository;
        this.mongoTemplate = mongoTemplate;
        this.placeCleanupService = placeCleanupService;
    }

    public String resolveInternalPlaceId(String source, String extId, double lat, double lng, String name) {
        final String normalizedSource = requireNotBlank(source, "source");
        final String normalizedExtId = requireNotBlank(extId, "extId");
        final String normalizedName = requireNotBlank(name, "name");
        validateLatitude(lat);
        validateLongitude(lng);

        LOGGER.info("Resolving internalPlaceId for source={}, externalId={}, name={}, coordinates=[{}, {}]",
                normalizedSource,
                normalizedExtId,
                normalizedName,
                lat,
                lng);

        final String lockKey = buildIdentityLockKey(lat, lng, normalizedName);
        return executeWithIdentityLock(lockKey,
                () -> resolveInternalPlaceIdUnderLock(normalizedSource, normalizedExtId, lat, lng, normalizedName));
    }

    private String resolveInternalPlaceIdUnderLock(String source,
                                                   String extId,
                                                   double lat,
                                                   double lng,
                                                   String name) {

        Optional<PlaceMapping> existingMapping = placeMappingRepository.findByExternalSourceAndExternalId(source, extId);
        if (existingMapping.isPresent()) {
            String internalId = existingMapping.get().getInternalPlaceId();
            if (isUsableMapping(existingMapping.get())) {
                LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Exact Match)", internalId, extId);
                return internalId;
            }
        }

        Point point = new Point(lng, lat);
        Query geoQuery = new Query(Criteria.where("location").withinSphere(
                new org.springframework.data.geo.Circle(point, new Distance(30.0 / 1000.0, Metrics.KILOMETERS))
        ));

        List<PlaceMapping> nearbyMappings = mongoTemplate.find(geoQuery, PlaceMapping.class);
        for (PlaceMapping mapping : nearbyMappings) {
            if (mapping == null || !isNameSimilar(mapping.getName(), name)) {
                continue;
            }

            if (isUsableMapping(mapping)) {
                String internalId = mapping.getInternalPlaceId();
                String resolvedId = upsertExternalMappingAndResolveInternalId(internalId, source, extId, lat, lng, name);
                ensurePlaceExists(resolvedId, source, lat, lng, name);

                LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (Spatial Match)", resolvedId, extId);
                return resolvedId;
            }
        }

        String candidateInternalId = UUID.randomUUID().toString();
        String resolvedId = upsertExternalMappingAndResolveInternalId(candidateInternalId, source, extId, lat, lng, name);
        ensurePlaceExists(resolvedId, source, lat, lng, name);

        LOGGER.info("Resolved internalPlaceId: {} for externalId: {} (New/Concurrent Mapping)", resolvedId, extId);
        return resolvedId;
    }

    private String upsertExternalMappingAndResolveInternalId(String candidateInternalId,
                                                             String source,
                                                             String extId,
                                                             double lat,
                                                             double lng,
                                                             String name) {
        PlaceMapping mapping;
        try {
            mapping = placeMappingRepository.upsertByExternalKey(
                source,
                extId,
                candidateInternalId,
                name,
                lat,
                lng);
        } catch (DataIntegrityViolationException ex) {
            mapping = placeMappingRepository.findByExternalSourceAndExternalId(source, extId)
                    .orElseThrow(() -> ex);
        }

        if (mapping == null) {
            mapping = placeMappingRepository.findByExternalSourceAndExternalId(source, extId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to upsert place mapping for source=" + source + ", extId=" + extId));
        }

        String resolvedInternalId = trimToNull(mapping.getInternalPlaceId());
        return resolvedInternalId != null ? resolvedInternalId : candidateInternalId;
    }

    private void ensurePlaceExists(String internalId,
                                   String source,
                                   double lat,
                                   double lng,
                                   String name) {
        Query placeQuery = Query.query(Criteria.where("_id").is(internalId));
        Update placeUpdate = new Update()
                .setOnInsert("_id", internalId)
                .setOnInsert("name", name)
                .setOnInsert("address", "")
                .setOnInsert("location", new Location(lat, lng))
                .setOnInsert("placeSource", source)
                .setOnInsert("persistedByAction", "resolved")
                .setOnInsert("deleted", false)
                .setOnInsert("serverVersion", 0L);
        mongoTemplate.upsert(placeQuery, placeUpdate, Place.class);

        placeCleanupService.reviveOrphanedPlace(internalId);
    }

    private boolean isUsableMapping(PlaceMapping mapping) {
        String internalId = mapping != null ? trimToNull(mapping.getInternalPlaceId()) : null;
        if (internalId == null) {
            return false;
        }

        long matchedCount = placeCleanupService.reviveOrphanedPlace(internalId);
        if (matchedCount > 0) {
            return true;
        }

        LOGGER.warn("Hard-delete detected. Removing orphaned PlaceMapping id={}, internalPlaceId={}",
                mapping.getId(),
                internalId);
        placeMappingRepository.delete(mapping);
        return false;
    }

    private String executeWithIdentityLock(String lockKey, Supplier<String> resolver) {
        String ownerToken = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= LOCK_RETRY_ATTEMPTS; attempt++) {
            if (tryAcquireIdentityLock(lockKey, ownerToken)) {
                try {
                    return resolver.get();
                } finally {
                    releaseIdentityLock(lockKey, ownerToken);
                }
            }

            if (attempt < LOCK_RETRY_ATTEMPTS) {
                try {
                    Thread.sleep(LOCK_RETRY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for place identity lock", interrupted);
                }
            }
        }

        throw new IllegalStateException("Could not acquire place identity lock");
    }

    private boolean tryAcquireIdentityLock(String lockKey, String ownerToken) {
        Instant now = Instant.now();
        Query acquireQuery = Query.query(new Criteria().andOperator(
                Criteria.where("_id").is(lockKey),
                new Criteria().orOperator(
                        Criteria.where("expiresAt").lte(now),
                        Criteria.where("ownerToken").is(ownerToken),
                        Criteria.where("ownerToken").exists(false)
                )
        ));

        Update acquireUpdate = new Update()
                .set("ownerToken", ownerToken)
                .set("acquiredAt", now)
                .set("expiresAt", now.plusSeconds(5))
                .setOnInsert("createdAt", now);

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);

        try {
            PlaceIdentityLock lock = mongoTemplate.findAndModify(
                    acquireQuery,
                    acquireUpdate,
                    options,
                    PlaceIdentityLock.class);
            return lock != null;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    private void releaseIdentityLock(String lockKey, String ownerToken) {
        Query releaseQuery = Query.query(
                Criteria.where("_id").is(lockKey)
                        .and("ownerToken").is(ownerToken));
        mongoTemplate.remove(releaseQuery, PlaceIdentityLock.class);
    }

    private String buildIdentityLockKey(double lat, double lng, String name) {
        long latBucket = Math.round(lat * LOCK_COORDINATE_SCALE);
        long lngBucket = Math.round(lng * LOCK_COORDINATE_SCALE);
        String seed = name.toLowerCase(Locale.ROOT) + "|" + latBucket + "|" + lngBucket;
        return "place-identity-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private String requireNotBlank(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private void validateLatitude(double lat) {
        if (!Double.isFinite(lat) || lat < -90.0d || lat > 90.0d) {
            throw new IllegalArgumentException("lat must be finite and between -90 and 90");
        }
    }

    private void validateLongitude(double lng) {
        if (!Double.isFinite(lng) || lng < -180.0d || lng > 180.0d) {
            throw new IllegalArgumentException("lng must be finite and between -180 and 180");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isNameSimilar(String name1, String name2) {
        if (name1 == null || name2 == null) {
            return false;
        }
        double similarity = calculateSimilarity(name1.toLowerCase(Locale.ROOT), name2.toLowerCase(Locale.ROOT));
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
