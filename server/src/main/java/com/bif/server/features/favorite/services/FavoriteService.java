package com.bif.server.features.favorite.services;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;

    public FavoriteService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
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
        return favoriteRepository.findByUserId(currentUserId);
    }

    public Optional<Favorite> getMyFavoriteById(String currentUserId, String favoriteId) {
        validateCurrentUserId(currentUserId);
        validateFavoriteId(favoriteId);
        return favoriteRepository.findByIdAndUserId(favoriteId, currentUserId);
    }

    public Favorite saveMyFavorite(String currentUserId, Favorite input) {
        validateCurrentUserId(currentUserId);
        if (input == null) {
            throw new IllegalArgumentException("favorite input must not be null");
        }

        String favoriteId = input.getId();
        if (favoriteId == null || favoriteId.isBlank()) {
            input.setUserId(currentUserId);
            return favoriteRepository.save(input);
        }

        Favorite existing = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new NoSuchElementException("favorite not found"));

        if (!currentUserId.equals(existing.getUserId())) {
            throw new SecurityException("favorite does not belong to current user");
        }

        input.setUserId(existing.getUserId());
        return favoriteRepository.save(input);
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
}
