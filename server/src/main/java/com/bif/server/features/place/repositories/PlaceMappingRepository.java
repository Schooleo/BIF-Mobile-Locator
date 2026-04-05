package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.PlaceMapping;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface PlaceMappingRepository extends MongoRepository<PlaceMapping, String> {
    Optional<PlaceMapping> findByExternalSourceAndExternalId(String externalSource, String externalId);
}
