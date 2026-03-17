package com.bif.server.features.favorite.controllers;

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
    public Favorite upsertFavorite(@Argument Favorite input) {
        return favoriteService.save(input);
    }

    @MutationMapping
    public Boolean deleteFavorite(@Argument String id) {
        return favoriteService.deleteById(id);
    }
}
