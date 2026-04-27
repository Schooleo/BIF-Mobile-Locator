package com.bif.server.features.favorite.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class FavoriteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FavoriteService.class);

    private final FavoriteRepository favoriteRepository;
    private final PlaceIdentityService placeIdentityService;
    private final PlaceRepository placeRepository;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           PlaceIdentityService placeIdentityService,
                           PlaceRepository placeRepository) {
        this.favoriteRepository = favoriteRepository;
        this.placeIdentityService = placeIdentityService;
        this.placeRepository = placeRepository;
    }

    public List<Favorite> getAll() {
        return favoriteRepository.findAll();
    }

    public List<Favorite> getByUserId(String userId) {
        return favoriteRepository.findByUserId(userId);
    }

    public Optional<Favorite> getById(String id) {
        return favoriteRepository.findById(id);
    }

    public Favorite save(Favorite favorite) {
        if (favorite == null) {
            throw new IllegalArgumentException("favorite must not be null");
        }

        normalizeIdentityMetadata(favorite);
        String resolvedPlaceId = resolvePlaceIdRequired(favorite);
        favorite.setPlaceId(resolvedPlaceId);
        favorite.setRating(resolveRatingSnapshot(resolvedPlaceId, favorite.getRating()));

        return favoriteRepository.save(favorite);
    }

    public boolean deleteById(String id) {
        if (!favoriteRepository.existsById(id)) {
            return false;
        }
        favoriteRepository.deleteById(id);
        return true;
    }

    public enum DeleteMyFavoriteResult {
        DELETED,
        NOT_FOUND,
        FORBIDDEN
    }

    public List<Favorite> getMyFavorites(String currentUserId) {
        validateCurrentUserId(currentUserId);
        return favoriteRepository.findByUserIdAndDeletedFalse(currentUserId);
    }

    public Optional<Favorite> getMyFavoriteById(String currentUserId, String favoriteId) {
        validateCurrentUserId(currentUserId);
        validateFavoriteId(favoriteId);
        return favoriteRepository.findByIdAndUserIdAndDeletedFalse(favoriteId,
                currentUserId);
    }

    public Favorite saveMyFavorite(String currentUserId, Favorite input) {
        validateCurrentUserId(currentUserId);
        if (input == null) {
            throw new IllegalArgumentException("favorite input must not be null");
        }

        String favoriteId = input.getId();
        if (favoriteId == null || favoriteId.isBlank()) {
            input.setUserId(currentUserId);
            return save(input);
        }

        Favorite existing = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new NoSuchElementException("favorite not found"));

        if (!currentUserId.equals(existing.getUserId())) {
            throw new SecurityException("favorite does not belong to current user");
        }

        input.setUserId(existing.getUserId());
        if (isBlank(input.getExternalSource()) && !isBlank(existing.getExternalSource())) {
            input.setExternalSource(existing.getExternalSource());
        }
        if (isBlank(input.getExternalId()) && !isBlank(existing.getExternalId())) {
            input.setExternalId(existing.getExternalId());
        }
        if (isBlank(input.getPlaceName()) && !isBlank(existing.getPlaceName())) {
            input.setPlaceName(existing.getPlaceName());
        }
        if (isBlank(input.getPlaceId()) && !isBlank(existing.getPlaceId())) {
            input.setPlaceId(existing.getPlaceId());
        }
        if (input.getLocation() == null && existing.getLocation() != null) {
            input.setLocation(existing.getLocation());
        }

        return save(input);
    }

    public DeleteMyFavoriteResult deleteMyFavorite(String currentUserId, String favoriteId) {
        validateCurrentUserId(currentUserId);
        validateFavoriteId(favoriteId);

        if (favoriteRepository.existsByIdAndUserId(favoriteId, currentUserId)) {
            favoriteRepository.deleteById(favoriteId);
            return DeleteMyFavoriteResult.DELETED;
        }

        if (favoriteRepository.existsById(favoriteId)) {
            return DeleteMyFavoriteResult.FORBIDDEN;
        }

        return DeleteMyFavoriteResult.NOT_FOUND;
    }

    private void validateCurrentUserId(String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new IllegalArgumentException("currentUserId must not be blank");
        }
    }

    private void validateFavoriteId(String favoriteId) {
        if (favoriteId == null || favoriteId.isBlank()) {
            throw new IllegalArgumentException("favoriteId must not be blank");
        }
    }

    private String resolvePlaceIdRequired(Favorite favorite) {
        if (favorite == null) {
            throw new IllegalArgumentException("favorite must not be null");
        }

        String legacyPlaceId = normalizeText(favorite.getPlaceId());
        String externalSource = normalizeText(favorite.getExternalSource());
        String externalId = normalizeText(favorite.getExternalId());
        if (!isBlank(legacyPlaceId) && isBlank(externalSource) && isBlank(externalId)) {
            return legacyPlaceId;
        }

        Location location = favorite.getLocation();
        if (location == null
                || !Double.isFinite(location.getLatitude())
                || !Double.isFinite(location.getLongitude())) {
            throw new IllegalArgumentException("favorite location is required for canonical place resolution");
        }

        if (isBlank(externalSource)) {
            throw new IllegalArgumentException("externalSource is required for canonical place resolution");
        }

        String placeName = normalizeText(favorite.getPlaceName());
        if (isBlank(placeName)) {
            placeName = normalizeText(favorite.getName());
        }
        if (isBlank(placeName)) {
            placeName = normalizeText(favorite.getAddress());
        }
        if (isBlank(placeName)) {
            throw new IllegalArgumentException("placeName, name, or address is required for canonical place resolution");
        }

        favorite.setPlaceName(placeName);

        try {
            String resolved = placeIdentityService.resolveInternalPlaceId(
                    externalSource,
                    externalId != null ? externalId.trim() : null,
                    location.getLatitude(),
                    location.getLongitude(),
                    placeName);
            if (isBlank(resolved)) {
                throw new IllegalStateException("place identity resolver returned empty placeId");
            }
            return resolved.trim();
        } catch (RuntimeException ex) {
            LOGGER.error("Failed to resolve placeId for favorite id={}, userId={}, name={}, lat={}, lng={}",
                    favorite.getId(),
                    favorite.getUserId(),
                    placeName,
                    location.getLatitude(),
                    location.getLongitude(),
                    ex);
            throw new IllegalStateException("Failed to resolve canonical placeId", ex);
        }
    }

    private void normalizeIdentityMetadata(Favorite favorite) {
        if (favorite == null) {
            return;
        }
        favorite.setExternalSource(normalizeText(favorite.getExternalSource()));
        favorite.setExternalId(normalizeText(favorite.getExternalId()));

        String placeName = normalizeText(favorite.getPlaceName());
        if (isBlank(placeName)) {
            placeName = normalizeText(favorite.getName());
        }
        if (isBlank(placeName)) {
            placeName = normalizeText(favorite.getAddress());
        }
        favorite.setPlaceName(placeName);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
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

}
