package com.bif.server.features.place.services.search;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class MongoPlaceSearchProvider implements PlaceSearchProvider {

    private final PlaceRepository placeRepository;

    public MongoPlaceSearchProvider(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        return placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                query, query);
    }
}