package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.Place;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface PlaceRepository extends MongoRepository<Place, String> {

    List<Place> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
            String name, String address);

    List<Place> findByTagsContaining(String tag);

    List<Place> findByPersistedByUserId(String userId);

    List<Place> findByDeletedFalse();

    @Query("{ 'serverVersion': { '$gt': ?0 } }")
    List<Place> findByServerVersionGreaterThan(long version);
}