package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.dto.rest.FavoriteResponse;
import com.bif.server.features.favorite.dto.rest.UpsertMyFavoriteRequest;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    @GetMapping("/me")
    public ResponseEntity<List<FavoriteResponse>> getMyFavorites(
            Authentication authentication
    ) {
        String currentUserId = currentUserId(authentication);
        if (currentUserId == null || currentUserId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        List<FavoriteResponse> result = favoriteService.getMyFavorites(currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<FavoriteResponse> getMyFavoriteById(
            Authentication authentication,
            @PathVariable String id
    ) {
        String currentUserId = currentUserId(authentication);
        if (currentUserId == null || currentUserId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        return favoriteService.getMyFavoriteById(currentUserId, id)
                .map(favorite -> ResponseEntity.ok(toResponse(favorite)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/me")
    public ResponseEntity<FavoriteResponse> upsertMyFavorite(
            Authentication authentication,
            @RequestBody UpsertMyFavoriteRequest request
    ) {
        String currentUserId = currentUserId(authentication);
        if (currentUserId == null || currentUserId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        Favorite input = new Favorite();
        input.setId(request.id());
        input.setExternalSource(request.externalSource());
        input.setExternalId(request.externalId());
        input.setPlaceName(request.placeName());
        input.setName(request.name());
        input.setLocation(request.location());
        input.setAddress(request.address());
        input.setDescription(request.description());
        input.setNotes(request.notes());
        input.setRating(request.rating());
        input.setImagePath(request.imagePath());

        try {
            Favorite saved = favoriteService.saveMyFavorite(currentUserId, input);
            return ResponseEntity.ok(toResponse(saved));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/me/{id}")
    public ResponseEntity<Void> deleteMyFavorite(
            Authentication authentication,
            @PathVariable String id
    ) {
        String currentUserId = currentUserId(authentication);
        if (currentUserId == null || currentUserId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        try {
            FavoriteService.DeleteMyFavoriteResult result = favoriteService.deleteMyFavorite(currentUserId, id);
            return switch (result) {
                case DELETED -> ResponseEntity.noContent().build();
                case FORBIDDEN -> ResponseEntity.status(403).build();
                case NOT_FOUND -> ResponseEntity.notFound().build();
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return authentication.getPrincipal().toString();
    }

    private FavoriteResponse toResponse(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getId(),
                favorite.getPlaceId(),
                favorite.getExternalSource(),
                favorite.getName(),
                favorite.getLocation(),
                favorite.getAddress(),
                favorite.getDescription(),
                favorite.getNotes(),
                favorite.getRating(),
                favorite.getImagePath(),
                favorite.getServerVersion(),
                favorite.getUpdatedAt()
        );
    }
}
