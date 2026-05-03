package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

@Component
public class FavoriteSyncEntityHandler implements SyncEntityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteSyncEntityHandler.class);

    private final FavoriteRepository favoriteRepository;
    private final PlaceIdentityService placeIdentityService;
    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FavoriteSyncEntityHandler(FavoriteRepository favoriteRepository,
                                     PlaceIdentityService placeIdentityService,
                                     PlaceRepository placeRepository) {
        this.favoriteRepository = favoriteRepository;
        this.placeIdentityService = placeIdentityService;
        this.placeRepository = placeRepository;
    }

    @Override
    public String entityType() {
        return "favorite";
    }

    @Override
    public SyncPushApplyResult applyPushedChangeResult(SyncChange pushed,
                                                       String userId,
                                                       LongSupplier nextVersionSupplier) {
        FavoritePayload payload = parseFavoritePayload(pushed != null ? pushed.getPayload() : null);
        String operation = pushed != null && pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        if (!"DELETE".equals(operation)) {
            if (payload == null || isBlank(payload.id)) {
                return SyncPushApplyResult.rejectedValidation("INVALID_FAVORITE_PAYLOAD");
            }
            Favorite existingFavorite = favoriteRepository.findByIdAndUserId(payload.id, userId).orElse(null);
            if (!canResolveCanonicalPlaceId(payload, existingFavorite != null ? existingFavorite.getLocation() : null)) {
                return SyncPushApplyResult.rejectedValidation("MISSING_CANONICAL_IDENTITY_SEED");
            }
        }

        long newVersion = nextVersionSupplier.getAsLong();
        return SyncPushApplyResult.applied(applyPushedChange(pushed, userId, newVersion), newVersion);
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId, long newVersion) {
        FavoritePayload payload = parseFavoritePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        if ("DELETE".equals(operation)) {
            String targetId = pushed.getEntityId();
            if ((targetId == null || targetId.isBlank()) && payload != null) {
                targetId = payload.id;
            }
            if (targetId == null || targetId.isBlank()) {
                return pushed.getPayload();
            }

            final String finalTargetId = targetId;
            Favorite favorite = favoriteRepository.findByIdAndUserId(finalTargetId, userId)
                    .orElseGet(() -> {
                        Favorite tombstone = new Favorite();
                        tombstone.setId(finalTargetId);
                        tombstone.setUserId(userId);
                        return tombstone;
                    });
            favorite.setDeleted(true);
            favorite.setServerVersion(newVersion);
            favorite.setLastModifiedBy(userId);
            favoriteRepository.save(favorite);

            FavoritePayload responsePayload = toPayload(favorite);
            responsePayload.deleted = true;
            responsePayload.serverVersion = newVersion;
            return writePayload(responsePayload);
        }

        if (payload == null || payload.id == null || payload.id.isBlank()) {
            return pushed.getPayload();
        }

        Favorite favorite = favoriteRepository.findByIdAndUserId(payload.id, userId)
                .orElseGet(Favorite::new);
        favorite.setId(payload.id);
        favorite.setName(payload.name);
        favorite.setAddress(payload.address);
        favorite.setDescription(payload.description);
        favorite.setNotes(payload.notes);
        favorite.setImagePath(payload.imagePath);
        favorite.setExternalSource(normalizeText(payload.externalSource));
        favorite.setExternalId(normalizeText(payload.externalId));
        favorite.setPlaceName(resolvePlaceName(payload));
        favorite.setUserId(userId);

        if (payload.latitude != 0.0 || payload.longitude != 0.0) {
            favorite.setLocation(new Location(payload.latitude, payload.longitude));
        } else if (payload.latitude == 0.0 && payload.longitude == 0.0 && favorite.getLocation() == null) {
            favorite.setLocation(null);
        }

        String resolvedPlaceId = resolvePlaceId(payload, favorite.getLocation());
        if (isBlank(resolvedPlaceId)) {
            throw new IllegalStateException("Unable to resolve canonical favorite placeId");
        }
        String normalizedPlaceId = resolvedPlaceId.trim();
        favorite.setPlaceId(normalizedPlaceId);
        favorite.setRating(resolveRatingSnapshot(normalizedPlaceId, payload.rating));

        favorite.setDeleted(payload.deleted);
        favorite.setServerVersion(newVersion);
        favorite.setLastModifiedBy(userId);
        favoriteRepository.save(favorite);

        FavoritePayload responsePayload = toPayload(favorite);
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            return entry.getPayload();
        }

        Optional<Favorite> favoriteOpt = favoriteRepository.findByIdAndUserId(entry.getEntityId(), entry.getUserId());
        if (favoriteOpt.isEmpty()) {
            return null;
        }

        FavoritePayload payload = toPayload(favoriteOpt.get());
        payload.serverVersion = entry.getServerVersion();
        payload.deleted = favoriteOpt.get().isDeleted();
        return writePayload(payload);
    }

    private FavoritePayload parseFavoritePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FavoritePayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(FavoritePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private FavoritePayload toPayload(Favorite favorite) {
        FavoritePayload payload = new FavoritePayload();
        payload.id = favorite.getId();
        payload.placeId = favorite.getPlaceId();
        payload.externalSource = favorite.getExternalSource();
        payload.externalId = favorite.getExternalId();
        payload.placeName = favorite.getPlaceName();
        payload.name = favorite.getName();
        payload.address = favorite.getAddress();
        payload.description = favorite.getDescription();
        payload.notes = favorite.getNotes();
        payload.rating = favorite.getRating();
        payload.imagePath = favorite.getImagePath();
        if (favorite.getLocation() != null) {
            payload.latitude = favorite.getLocation().getLatitude();
            payload.longitude = favorite.getLocation().getLongitude();
        } else {
            payload.latitude = 0;
            payload.longitude = 0;
        }
        payload.userId = favorite.getUserId();
        payload.serverVersion = favorite.getServerVersion();
        payload.deleted = favorite.isDeleted();
        return payload;
    }

    private String resolvePlaceId(FavoritePayload payload, Location fallbackLocation) {
        if (payload == null || isBlank(payload.externalSource)) {
            return null;
        }

        Double latitude = null;
        Double longitude = null;

        if (Double.isFinite(payload.latitude)
                && Double.isFinite(payload.longitude)
                && !(payload.latitude == 0.0d && payload.longitude == 0.0d)) {
            latitude = payload.latitude;
            longitude = payload.longitude;
        } else if (fallbackLocation != null
                && Double.isFinite(fallbackLocation.getLatitude())
                && Double.isFinite(fallbackLocation.getLongitude())
                && !(fallbackLocation.getLatitude() == 0.0d && fallbackLocation.getLongitude() == 0.0d)) {
            latitude = fallbackLocation.getLatitude();
            longitude = fallbackLocation.getLongitude();
        }

        if (latitude == null || longitude == null) {
            return null;
        }

        String placeName = resolvePlaceName(payload);
        if (isBlank(placeName)) {
            return null;
        }

        try {
            return placeIdentityService.resolveInternalPlaceId(
                    payload.externalSource.trim(),
                    payload.externalId != null ? payload.externalId.trim() : null,
                    latitude,
                    longitude,
                    placeName);
        } catch (DataAccessException ex) {
            LOGGER.error("Failed to resolve favorite placeId for payload externalSource={}, externalId={}, placeName={}, lat={}, lng={}",
                    payload.externalSource,
                    payload.externalId,
                    placeName,
                    latitude,
                    longitude,
                    ex);
            return null;
        }
    }

    private boolean canResolveCanonicalPlaceId(FavoritePayload payload, Location fallbackLocation) {
        if (payload == null || isBlank(payload.externalSource)) {
            return false;
        }

        boolean hasPayloadLocation = Double.isFinite(payload.latitude)
                && Double.isFinite(payload.longitude)
                && !(payload.latitude == 0.0d && payload.longitude == 0.0d);
        boolean hasFallbackLocation = fallbackLocation != null
                && Double.isFinite(fallbackLocation.getLatitude())
                && Double.isFinite(fallbackLocation.getLongitude())
                && !(fallbackLocation.getLatitude() == 0.0d && fallbackLocation.getLongitude() == 0.0d);
        if (!hasPayloadLocation && !hasFallbackLocation) {
            return false;
        }
        return !isBlank(resolvePlaceName(payload));
    }

    private String resolvePlaceName(FavoritePayload payload) {
        if (payload == null) {
            return null;
        }
        String placeName = normalizeText(payload.placeName);
        if (isBlank(placeName)) {
            placeName = normalizeText(payload.name);
        }
        if (isBlank(placeName)) {
            placeName = normalizeText(payload.address);
        }
        return placeName;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int resolveRatingSnapshot(String placeId, int fallbackRating) {
        if (isBlank(placeId)) {
            return fallbackRating;
        }

        return placeRepository.findById(placeId.trim())
                .map(place -> (int) Math.round(place.getRating()))
                .orElse(fallbackRating);
    }

    private static class FavoritePayload {
        public String id;
        public String placeId;
        public String externalSource;
        public String externalId;
        public String placeName;
        public String name;
        public String address;
        public String description;
        public String notes;
        public int rating;
        public String imagePath;
        public double latitude;
        public double longitude;
        public String userId;
        public long serverVersion;
        public boolean deleted;
    }
}
