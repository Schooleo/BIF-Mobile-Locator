package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class FavoriteSyncEntityHandler implements SyncEntityHandler {

    private final FavoriteRepository favoriteRepository;
    private final PlaceIdentityService placeIdentityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FavoriteSyncEntityHandler(FavoriteRepository favoriteRepository,
                                     PlaceIdentityService placeIdentityService) {
        this.favoriteRepository = favoriteRepository;
        this.placeIdentityService = placeIdentityService;
    }

    @Override
    public String entityType() {
        return "favorite";
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
        favorite.setRating(payload.rating);
        favorite.setImagePath(payload.imagePath);
        favorite.setUserId(userId);
        if (!isBlank(payload.placeId)) {
            favorite.setPlaceId(payload.placeId.trim());
        }

        if (payload.latitude != 0.0 || payload.longitude != 0.0) {
            favorite.setLocation(new Location(payload.latitude, payload.longitude));
        } else if (payload.latitude == 0.0 && payload.longitude == 0.0 && favorite.getLocation() == null) {
            favorite.setLocation(null);
        }

        if (isBlank(favorite.getPlaceId())) {
            String resolvedPlaceId = resolvePlaceId(payload);
            if (!isBlank(resolvedPlaceId)) {
                favorite.setPlaceId(resolvedPlaceId.trim());
            }
        }

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

    private String resolvePlaceId(FavoritePayload payload) {
        if (payload == null || isBlank(payload.id) || isBlank(payload.name)) {
            return null;
        }
        if (!Double.isFinite(payload.latitude) || !Double.isFinite(payload.longitude)) {
            return null;
        }
        if (payload.latitude == 0.0d && payload.longitude == 0.0d) {
            return null;
        }
        try {
            return placeIdentityService.resolveInternalPlaceId(
                    "FAVORITE",
                    payload.id.trim(),
                    payload.latitude,
                    payload.longitude,
                    payload.name.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class FavoritePayload {
        public String id;
        public String placeId;
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
