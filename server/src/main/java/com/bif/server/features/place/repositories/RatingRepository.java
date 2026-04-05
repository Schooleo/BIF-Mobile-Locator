package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.PlaceReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RatingRepository extends MongoRepository<PlaceReview, String> {

    Optional<PlaceReview> findByUserIdAndPlaceId(String userId, String placeId);

    Page<PlaceReview> findByPlaceIdOrderByCreatedAtDesc(String placeId, Pageable pageable);
    java.util.List<PlaceReview> findByPlaceIdOrderByCreatedAtDesc(String placeId);
}
