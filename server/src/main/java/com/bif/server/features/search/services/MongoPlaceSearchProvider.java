package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Qualifier("mongoPlaceSearchProvider")
public class MongoPlaceSearchProvider implements PlaceSearchProvider {

    private final PlaceRepository placeRepository;

    public MongoPlaceSearchProvider(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public List<Place> search(PlaceSearchRequestDTO request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return Collections.emptyList();
        }
        String query = request.getQuery();
        return placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                query, query);
    }

    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery(query);
        return search(request);
    }
}
