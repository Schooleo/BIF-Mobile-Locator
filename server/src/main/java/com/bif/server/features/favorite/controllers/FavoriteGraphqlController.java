package com.bif.server.features.favorite.controllers;

import com.bif.server.features.favorite.dto.graphql.DeleteMyFavoriteResult;
import com.bif.server.features.favorite.dto.graphql.UpsertFavoriteInput;
import com.bif.server.features.favorite.models.Favorite;
import com.bif.server.features.favorite.services.FavoriteService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class FavoriteGraphqlController {
    private final FavoriteService favoriteService;

    public FavoriteGraphqlController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @QueryMapping
    public List<Favorite> favorites() {
        return favoriteService.getAll();
    }

    @QueryMapping
    public Favorite favorite(@Argument String id) {
        return favoriteService.getById(id).orElse(null);
    }

    @QueryMapping
    public List<Favorite> favoritesByUser(@Argument String userId) {
        return favoriteService.getByUserId(userId);
    }

    @MutationMapping
    public Favorite upsertFavorite(@Argument UpsertFavoriteInput input) {
        return favoriteService.save(toFavorite(input));
    }

    @MutationMapping
    public Boolean deleteFavorite(@Argument String id) {
        return favoriteService.deleteById(id);
    }

    @QueryMapping
    public List<Favorite> myFavorites(@Argument String userId) {
        requireUserId(userId);
        return favoriteService.getMyFavorites(userId);
    }

    @MutationMapping
    public Favorite upsertMyFavorite(@Argument String userId,
                                     @Argument UpsertFavoriteInput input) {
        requireUserId(userId);
        return favoriteService.saveMyFavorite(userId, toFavorite(input));
    }

    @MutationMapping
    public DeleteMyFavoriteResult deleteMyFavorite(@Argument String userId, @Argument String id) {
        requireUserId(userId);

        FavoriteService.DeleteMyFavoriteResult result = favoriteService.deleteMyFavorite(userId, id);
        return switch (result) {
            case DELETED -> DeleteMyFavoriteResult.DELETED;
            case FORBIDDEN -> DeleteMyFavoriteResult.FORBIDDEN;
            case NOT_FOUND -> DeleteMyFavoriteResult.NOT_FOUND;
        };
    }

    private void requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
    }

    private Favorite toFavorite(UpsertFavoriteInput input) {
        if (input == null) {
            throw new IllegalArgumentException("favorite input is required");
        }

        Favorite favorite = new Favorite();
        favorite.setId(input.id());
        favorite.setExternalSource(input.externalSource());
        favorite.setExternalId(input.externalId());
        favorite.setPlaceName(input.placeName());
        favorite.setName(input.name());
        favorite.setLocation(input.location());
        favorite.setAddress(input.address());
        favorite.setDescription(input.description());
        favorite.setNotes(input.notes());
        favorite.setRating(input.rating());
        favorite.setImagePath(input.imagePath());
        favorite.setUserId(input.userId());
        return favorite;
    }
}
