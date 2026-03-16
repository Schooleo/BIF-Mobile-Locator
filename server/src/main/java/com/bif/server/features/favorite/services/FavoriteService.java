package com.bif.server.features.favorite.services;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.repositories.FavoriteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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
}
