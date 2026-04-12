package com.bif.server.features.favorite.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final PlaceIdentityService placeIdentityService;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           PlaceIdentityService placeIdentityService) {
        this.favoriteRepository = favoriteRepository;
        this.placeIdentityService = placeIdentityService;
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

        Favorite saved = favoriteRepository.save(favorite);
        return backfillPlaceIdIfMissing(saved);
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
            Favorite created = favoriteRepository.save(input);
            return backfillPlaceIdIfMissing(created);
        }

        Favorite existing = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new NoSuchElementException("favorite not found"));

        if (!currentUserId.equals(existing.getUserId())) {
            throw new SecurityException("favorite does not belong to current user");
        }

        input.setUserId(existing.getUserId());
        if (isBlank(input.getPlaceId()) && !isBlank(existing.getPlaceId())) {
            input.setPlaceId(existing.getPlaceId());
        }

        Favorite updated = favoriteRepository.save(input);
        return backfillPlaceIdIfMissing(updated);
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

    private Favorite backfillPlaceIdIfMissing(Favorite favorite) {
        if (favorite == null || !isBlank(favorite.getPlaceId())) {
            return favorite;
        }

        Location location = favorite.getLocation();
        if (location == null || !Double.isFinite(location.getLatitude()) || !Double.isFinite(location.getLongitude())) {
            return favorite;
        }

        String placeName = normalizeText(favorite.getName());
        if (isBlank(placeName)) {
            placeName = normalizeText(favorite.getAddress());
        }
        if (isBlank(placeName)) {
            return favorite;
        }

        String externalId = favorite.getId();
        if (isBlank(externalId)) {
            return favorite;
        }

        String resolvedPlaceId;
        try {
            resolvedPlaceId = placeIdentityService.resolveInternalPlaceId(
                    "FAVORITE",
                    externalId.trim(),
                    location.getLatitude(),
                    location.getLongitude(),
                    placeName);
        } catch (RuntimeException ex) {
            return favorite;
        }
        if (isBlank(resolvedPlaceId)) {
            return favorite;
        }

        favorite.setPlaceId(resolvedPlaceId.trim());
        return favoriteRepository.save(favorite);
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
}
