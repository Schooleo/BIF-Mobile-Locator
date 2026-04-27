package com.bif.server.common.migration;

import com.bif.server.common.migration.model.SchemaMigration;
import com.bif.server.common.migration.repository.SchemaMigrationRepository;
import com.bif.server.common.models.Location;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Order(2)
@Profile("!test")
public class FavoriteContractV2MigrationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteContractV2MigrationRunner.class);
    private static final String MIGRATION_ID = "favorite-contract-v2";
    private static final String FAVORITES_COLLECTION = "favorites";

    private final MongoTemplate mongoTemplate;
    private final PlaceIdentityService placeIdentityService;
    private final SchemaMigrationRepository schemaMigrationRepository;

    @Value("${app.migration.favorite-v2.enabled:true}")
    private boolean migrationEnabled;

    public FavoriteContractV2MigrationRunner(MongoTemplate mongoTemplate,
                                             PlaceIdentityService placeIdentityService,
                                             SchemaMigrationRepository schemaMigrationRepository) {
        this.mongoTemplate = mongoTemplate;
        this.placeIdentityService = placeIdentityService;
        this.schemaMigrationRepository = schemaMigrationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) {
            LOGGER.info("Favorite contract v2 migration is disabled by config.");
            return;
        }
        if (schemaMigrationRepository.existsById(MIGRATION_ID)) {
            LOGGER.info("Favorite contract v2 migration already applied. Skipping.");
            return;
        }

        int scanned = 0;
        int migrated = 0;
        int skippedInvalidSeed = 0;
        int failed = 0;

        List<Document> favorites = mongoTemplate.findAll(Document.class, FAVORITES_COLLECTION);
        for (Document favorite : favorites) {
            scanned++;
            if (favorite == null || isDeleted(favorite)) {
                continue;
            }

            String favoriteId = normalize(readString(favorite, "_id"));
            String userId = normalize(readString(favorite, "userId"));
            String externalSource = normalize(readString(favorite, "externalSource"));
            String externalId = normalize(readString(favorite, "externalId"));
            String placeName = resolvePlaceName(favorite);
            Location location = extractLocation(favorite.get("location"));
            boolean resolvable = canResolve(externalSource, placeName, location);

            if (!resolvable) {
                skippedInvalidSeed++;
                LOGGER.warn("Skipping favorite migration due to missing identity seed. favoriteId={}, userId={}",
                        favoriteId,
                        userId);
                continue;
            }
            if (isBlank(favoriteId)) {
                failed++;
                LOGGER.error("Skipping favorite migration because _id is missing. userId={}", userId);
                continue;
            }

            try {
                String canonicalPlaceId = placeIdentityService.resolveInternalPlaceId(
                        externalSource,
                        externalId,
                        location.getLatitude(),
                        location.getLongitude(),
                        placeName);
                if (canonicalPlaceId == null || canonicalPlaceId.isBlank()) {
                    failed++;
                    LOGGER.error("Canonical placeId is blank during favorite migration. favoriteId={}, userId={}",
                            favoriteId,
                            userId);
                    continue;
                }

                String normalizedCanonical = canonicalPlaceId.trim();
                String existingPlaceId = normalize(readString(favorite, "placeId"));
                boolean changed = !normalizedCanonical.equals(existingPlaceId)
                        || hasLegacySnapshotFields(favorite);
                if (!changed) {
                    continue;
                }

                Update update = new Update()
                        .set("placeId", normalizedCanonical)
                        .unset("externalId")
                        .unset("placeName");

                UpdateResult updateResult = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(favoriteId)),
                        update,
                        FAVORITES_COLLECTION);
                if (updateResult.getMatchedCount() == 0) {
                    failed++;
                    LOGGER.error("Favorite not found during migration update. favoriteId={}, userId={}",
                            favoriteId,
                            userId);
                } else {
                    migrated++;
                }
            } catch (RuntimeException ex) {
                failed++;
                LOGGER.error("Failed to migrate favorite identity. favoriteId={}, userId={}",
                        favoriteId,
                        userId,
                        ex);
            }
        }

        if (failed == 0) {
            SchemaMigration marker = new SchemaMigration();
            marker.setId(MIGRATION_ID);
            marker.setExecutedAt(Instant.now());
            schemaMigrationRepository.save(marker);
        } else {
            LOGGER.warn("Favorite contract v2 migration finished with failures. Marker not persisted so failed rows can be retried. failed={}",
                    failed);
        }

        LOGGER.info("Favorite contract v2 migration complete. scanned={}, migrated={}, skippedInvalidSeed={}, failed={}",
                scanned,
                migrated,
                skippedInvalidSeed,
                failed);
    }

    private boolean hasLegacySnapshotFields(Document favorite) {
        if (favorite == null) {
            return false;
        }
        return favorite.containsKey("externalId") || favorite.containsKey("placeName");
    }

    private boolean isDeleted(Document favorite) {
        Object rawDeleted = favorite != null ? favorite.get("deleted") : null;
        if (rawDeleted instanceof Boolean deleted) {
            return deleted;
        }
        if (rawDeleted instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private boolean canResolve(String externalSource, String placeName, Location location) {
        if (location == null
                || !Double.isFinite(location.getLatitude())
                || !Double.isFinite(location.getLongitude())
                || location.getLatitude() == 0.0d
                || location.getLongitude() == 0.0d) {
            return false;
        }

        if (isBlank(externalSource)) {
            return false;
        }
        return !isBlank(placeName);
    }

    private Location extractLocation(Object rawLocation) {
        if (!(rawLocation instanceof Document locationDoc)) {
            return null;
        }

        Double latitude = toDouble(locationDoc.get("latitude"));
        Double longitude = toDouble(locationDoc.get("longitude"));
        if (latitude == null || longitude == null) {
            return null;
        }
        return new Location(latitude, longitude);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String resolvePlaceName(Document favorite) {
        String placeName = normalize(readString(favorite, "placeName"));
        if (isBlank(placeName)) {
            placeName = normalize(readString(favorite, "name"));
        }
        if (isBlank(placeName)) {
            placeName = normalize(readString(favorite, "address"));
        }
        return placeName;
    }

    private String readString(Document favorite, String key) {
        if (favorite == null || key == null) {
            return null;
        }
        Object value = favorite.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
