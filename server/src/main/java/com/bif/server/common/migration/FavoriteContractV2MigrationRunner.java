package com.bif.server.common.migration;

import com.bif.server.common.migration.model.SchemaMigration;
import com.bif.server.common.migration.repository.SchemaMigrationRepository;
import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@Order(2)
@Profile("!test")
public class FavoriteContractV2MigrationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteContractV2MigrationRunner.class);
    private static final String MIGRATION_ID = "favorite-contract-v2";

    private final FavoriteRepository favoriteRepository;
    private final PlaceIdentityService placeIdentityService;
    private final SchemaMigrationRepository schemaMigrationRepository;

    @Value("${app.migration.favorite-v2.enabled:true}")
    private boolean migrationEnabled;

    public FavoriteContractV2MigrationRunner(FavoriteRepository favoriteRepository,
                                             PlaceIdentityService placeIdentityService,
                                             SchemaMigrationRepository schemaMigrationRepository) {
        this.favoriteRepository = favoriteRepository;
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

        List<Favorite> favorites = favoriteRepository.findAll();
        for (Favorite favorite : favorites) {
            scanned++;
            if (favorite == null || favorite.isDeleted()) {
                continue;
            }

            String externalSource = normalize(favorite.getExternalSource());
            String externalId = normalize(favorite.getExternalId());
            String placeName = resolvePlaceName(favorite);
            boolean resolvable = canResolve(favorite);
            boolean changed = clearLegacySnapshotFields(favorite);

            if (!resolvable) {
                skippedInvalidSeed++;
                LOGGER.warn("Skipping favorite migration due to missing identity seed. favoriteId={}, userId={}",
                        favorite != null ? favorite.getId() : null,
                        favorite != null ? favorite.getUserId() : null);
                if (changed) {
                    favoriteRepository.save(favorite);
                    migrated++;
                }
                continue;
            }

            try {
                String canonicalPlaceId = placeIdentityService.resolveInternalPlaceId(
                        externalSource,
                        externalId,
                        favorite.getLocation().getLatitude(),
                        favorite.getLocation().getLongitude(),
                        placeName);
                if (canonicalPlaceId == null || canonicalPlaceId.isBlank()) {
                    failed++;
                    LOGGER.error("Canonical placeId is blank during favorite migration. favoriteId={}, userId={}",
                            favorite.getId(),
                            favorite.getUserId());
                    continue;
                }

                String normalizedCanonical = canonicalPlaceId.trim();
                if (!normalizedCanonical.equals(favorite.getPlaceId())) {
                    favorite.setPlaceId(normalizedCanonical);
                    changed = true;
                }

                if (changed) {
                    favoriteRepository.save(favorite);
                    migrated++;
                }
            } catch (RuntimeException ex) {
                failed++;
                LOGGER.error("Failed to migrate favorite identity. favoriteId={}, userId={}",
                        favorite.getId(),
                        favorite.getUserId(),
                        ex);
            }
        }

        SchemaMigration marker = new SchemaMigration();
        marker.setId(MIGRATION_ID);
        marker.setExecutedAt(Instant.now());
        schemaMigrationRepository.save(marker);

        LOGGER.info("Favorite contract v2 migration complete. scanned={}, migrated={}, skippedInvalidSeed={}, failed={}",
                scanned,
                migrated,
                skippedInvalidSeed,
                failed);
    }

    private boolean clearLegacySnapshotFields(Favorite favorite) {
        if (favorite == null) {
            return false;
        }

        boolean changed = false;
        if (favorite.getExternalId() != null) {
            favorite.setExternalId(null);
            changed = true;
        }
        if (favorite.getPlaceName() != null) {
            favorite.setPlaceName(null);
            changed = true;
        }
        return changed;
    }

    private boolean canResolve(Favorite favorite) {
        if (favorite == null) {
            return false;
        }
        Location location = favorite.getLocation();
        if (location == null
                || !Double.isFinite(location.getLatitude())
                || !Double.isFinite(location.getLongitude())
                || location.getLatitude() == 0.0d
                || location.getLongitude() == 0.0d) {
            return false;
        }

        if (isBlank(favorite.getExternalSource())) {
            return false;
        }
        return !isBlank(resolvePlaceName(favorite));
    }

    private String resolvePlaceName(Favorite favorite) {
        String placeName = normalize(favorite.getPlaceName());
        if (isBlank(placeName)) {
            placeName = normalize(favorite.getName());
        }
        if (isBlank(placeName)) {
            placeName = normalize(favorite.getAddress());
        }
        return placeName;
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
