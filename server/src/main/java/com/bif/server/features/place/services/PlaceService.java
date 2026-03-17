package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PlaceService {
    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public List<Place> getAll() {
        return placeRepository.findAll();
    }

    public Optional<Place> getById(String id) {
        return placeRepository.findById(id);
    }

    public Place save(Place place) {
        return placeRepository.save(place);
    }

    public boolean deleteById(String id) {
        if (!placeRepository.existsById(id)) {
            return false;
        }
        placeRepository.deleteById(id);
        return true;
    }
}
