package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteRestController {
    private final FavoriteService favoriteService;

    public FavoriteRestController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<Favorite> getFavorites() {
        return favoriteService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Favorite> getFavoriteById(@PathVariable String id) {
        return favoriteService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<Favorite> getFavoritesByUser(@PathVariable String userId) {
        return favoriteService.getByUserId(userId);
    }

    @PostMapping
    public Favorite upsertFavorite(@RequestBody Favorite favorite) {
        return favoriteService.save(favorite);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFavorite(@PathVariable String id) {
        return favoriteService.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
