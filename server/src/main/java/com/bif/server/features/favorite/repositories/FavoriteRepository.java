package com.bif.server.features.favorite.repositories;

import com.bif.server.features.favorite.models.Favorite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends MongoRepository<Favorite, String> {
    List<Favorite> findByUserId(String userId);
    Optional<Favorite> findByIdAndUserId(String id, String userId);
    boolean existsByIdAndUserId(String id, String userId);
}