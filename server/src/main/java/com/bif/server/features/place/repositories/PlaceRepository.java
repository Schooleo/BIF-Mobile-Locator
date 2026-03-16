package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.Place;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PlaceRepository extends MongoRepository<Place, String> {
}