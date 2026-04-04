package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class RatingService {
    private final RatingRepository ratingRepository;
    private final PlaceRatingCacheUpdater placeRatingCacheUpdater;
    private final PlaceSearchIndexSyncService placeSearchIndexSyncService;

    public RatingService(RatingRepository ratingRepository,
                         PlaceRatingCacheUpdater placeRatingCacheUpdater,
                         PlaceSearchIndexSyncService placeSearchIndexSyncService) {
        this.ratingRepository = ratingRepository;
        this.placeRatingCacheUpdater = placeRatingCacheUpdater;
        this.placeSearchIndexSyncService = placeSearchIndexSyncService;
    }

    @Transactional
    public PlaceReview saveReview(String userId, String placeId, ReviewDTO dto) {
        String resolvedUserId = required(userId, "userId");
        String resolvedPlaceId = required(placeId, "placeId");
        validateStars(dto.stars());

        PlaceReview review = new PlaceReview();
        review.setUserId(resolvedUserId);
        review.setPlaceId(resolvedPlaceId);
        review.setStars(dto.stars());
        review.setComment(normalizeComment(dto.comment()));
        review.setCreatedAt(LocalDateTime.now());

        final PlaceReview persistedReview;
        try {
            persistedReview = ratingRepository.save(review);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("User has already reviewed this place", e);
        }

        try {
            Place updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
                    resolvedUserId,
                    resolvedPlaceId,
                    dto.stars());
            placeSearchIndexSyncService.upsert(updatedPlace);
            return persistedReview;
        } catch (RuntimeException e) {
            ratingRepository.deleteById(persistedReview.getId());
            if (e instanceof NoSuchElementException) {
                throw e;
            }
            throw new IllegalStateException("Unable to update place rating cache", e);
        }
    }

    public Optional<PlaceReview> getUserReview(String userId, String placeId) {
        String resolvedUserId = required(userId, "userId");
        String resolvedPlaceId = required(placeId, "placeId");
        return ratingRepository.findByUserIdAndPlaceId(resolvedUserId, resolvedPlaceId);
    }

    public Page<PlaceReview> getPlaceReviews(String placeId, Pageable pageable) {
        String resolvedPlaceId = required(placeId, "placeId");
        return ratingRepository.findByPlaceIdOrderByCreatedAtDesc(resolvedPlaceId, pageable);
    }

    @Transactional
    public void deleteReview(String userId, String placeId) {
        String resolvedUserId = required(userId, "userId");
        String resolvedPlaceId = required(placeId, "placeId");

        PlaceReview review = ratingRepository.findByUserIdAndPlaceId(resolvedUserId, resolvedPlaceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Review not found for user " + resolvedUserId + " and place " + resolvedPlaceId));

        Place updatedPlace = placeRatingCacheUpdater.decrementAndRecalculate(
                resolvedUserId,
                resolvedPlaceId,
                review.getStars());

        ratingRepository.deleteById(review.getId());
        placeSearchIndexSyncService.upsert(updatedPlace);
    }

    private void validateStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("stars must be between 1 and 5");
        }
    }

    private String normalizeComment(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
