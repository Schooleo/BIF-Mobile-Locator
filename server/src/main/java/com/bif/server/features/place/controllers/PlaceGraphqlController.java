package com.bif.server.features.place.controllers;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.services.PlaceService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class PlaceGraphqlController {
    private final PlaceService placeService;

    public PlaceGraphqlController(PlaceService placeService) {
        this.placeService = placeService;
    }

    @QueryMapping
    public List<Place> places() {
        return placeService.getAll();
    }

    @QueryMapping
    public Place place(@Argument String id) {
        return placeService.getById(id).orElse(null);
    }

    @MutationMapping
    public Place upsertPlace(@Argument Place input) {
        return placeService.save(input);
    }

    @MutationMapping
    public Boolean deletePlace(@Argument String id) {
        return placeService.deleteById(id);
    }
}
