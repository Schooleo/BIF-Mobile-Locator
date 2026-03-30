package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.Place;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface PlaceRepository extends MongoRepository<Place, String> {

    @Query("{ 'name': ?0, 'location.latitude': ?1, 'location.longitude': ?2 }")
    List<Place> findByNameAndLocationLatitudeAndLocationLongitude(String name, double latitude, double longitude);

    List<Place> findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
            String name, String address);

    List<Place> findByTagsContaining(String tag);

    List<Place> findByPersistedByUserId(String userId);

    List<Place> findByDeletedFalse();

    @Query("{ 'serverVersion': { '$gt': ?0 } }")
    List<Place> findByServerVersionGreaterThan(long version);
}