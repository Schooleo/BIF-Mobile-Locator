package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.TypesensePlaceIndexSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;

@Service
public class RatingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository;
    private final PlaceRatingCacheUpdater placeRatingCacheUpdater;
    private final PlaceSearchIndexSyncService placeSearchIndexSyncService;
    private final UserRepository userRepository;

    public RatingService(RatingRepository ratingRepository,
                         PlaceRatingCacheUpdater placeRatingCacheUpdater,
                         PlaceSearchIndexSyncService placeSearchIndexSyncService,
                         UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.placeRatingCacheUpdater = placeRatingCacheUpdater;
        this.placeSearchIndexSyncService = placeSearchIndexSyncService;
        this.userRepository = userRepository;
    }

    /**
     * Saves a review and updates the place rating cache sequentially.
     *
     * @throws DuplicateKeyException if a review already exists for this user/place
     */
    public ReviewResponseDTO saveReview(String userId, String placeId, ReviewDTO dto) {
        return saveReview(userId, placeId, placeId, dto);
    }

    public ReviewResponseDTO saveReview(String userId,
                                        String originalPlaceId,
                                        String resolvedPlaceId,
                                        ReviewDTO dto) {
        String resolvedUserId = required(userId, "userId");
        String normalizedOriginalPlaceId = required(originalPlaceId, "placeId");
        String normalizedResolvedPlaceId = required(resolvedPlaceId, "placeId");
        logPlaceIdCorrectionIfNeeded(normalizedOriginalPlaceId, normalizedResolvedPlaceId, "saveReview");
        validateStars(dto.stars());

        PlaceReview review = new PlaceReview();
        review.setUserId(resolvedUserId);
        review.setPlaceId(normalizedResolvedPlaceId);
        review.setStars(dto.stars());
        review.setComment(normalizeComment(dto.comment()));
        review.setExternalSource(normalizeNullable(dto.externalSource()));
        review.setExternalId(normalizeNullable(dto.externalId()));
        review.setLat(dto.lat());
        review.setLng(dto.lng());
        review.setPlaceName(normalizeNullable(dto.placeName()));
        review.setCreatedAt(dto.createdAt() > 0 ? Instant.ofEpochMilli(dto.createdAt()) : Instant.now());

        PlaceReview persistedReview;
        try {
            // Luu review. Duplicate placeId+userId should map to HTTP 409 upstream.
            persistedReview = ratingRepository.save(review);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateKeyException(
                "Review already exists for user " + resolvedUserId
                    + " and place " + normalizedResolvedPlaceId,
                ex);
        }

        // Cập nhật cache bằng optimistic retries tại PlaceRatingCacheUpdater.
        Place updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
            resolvedUserId, normalizedResolvedPlaceId, dto.stars());
        syncRatingToSearch(updatedPlace);

        return mapToReviewResponseDTO(persistedReview);
    }  

    /**
     * Saves or updates a review and updates the place rating cache sequentially.
     * Handles three scenarios:
     * 1. Existing review update - calls replaceAndRecalculate
     * 2. New review create - calls incrementAndRecalculate
     * 3. Concurrent race condition - retries with updated state
     */
    public ReviewResponseDTO saveOrUpdateReview(
            int stars,
            String comment,
            String userId,
            String placeId,
            long serverVersion
    ) {
        return saveOrUpdateReview(
                stars,
                comment,
                userId,
                placeId,
                placeId,
                serverVersion,
                null,
                null,
                null,
                null,
                null);
    }

    public ReviewResponseDTO saveOrUpdateReview(
            int stars,
            String comment,
            String userId,
            String originalPlaceId,
            String resolvedPlaceId,
            long serverVersion,
            String externalSource,
            String externalId,
            Double lat,
            Double lng,
            String placeName
    ) {
        if (serverVersion > 0) {
            LOGGER.debug("saveOrUpdateReview invoked from sync version {}", serverVersion);
        }

        String resolvedUserId = required(userId, "userId");
        String normalizedOriginalPlaceId = required(originalPlaceId, "placeId");
        String normalizedResolvedPlaceId = required(resolvedPlaceId, "placeId");
        logPlaceIdCorrectionIfNeeded(normalizedOriginalPlaceId, normalizedResolvedPlaceId, "saveOrUpdateReview");
        validateStars(stars);

        String normalizedComment = normalizeComment(comment);

        Optional<PlaceReview> existingOpt = ratingRepository
            .findByUserIdAndPlaceId(resolvedUserId, normalizedResolvedPlaceId);

        if (existingOpt.isPresent()) {
            PlaceReview existingReview = existingOpt.get();
            int oldStars = existingReview.getStars();
            existingReview.setStars(stars);
            existingReview.setComment(normalizedComment);
            existingReview.setExternalSource(normalizeNullable(externalSource));
            existingReview.setExternalId(normalizeNullable(externalId));
            existingReview.setLat(lat);
            existingReview.setLng(lng);
            existingReview.setPlaceName(normalizeNullable(placeName));
            if (existingReview.getCreatedAt() == null) {
                existingReview.setCreatedAt(Instant.now());
            }

            PlaceReview persisted = ratingRepository.save(existingReview);
            if (oldStars != stars) {
                Place updatedPlace = placeRatingCacheUpdater.replaceAndRecalculate(
                        resolvedUserId,
                        normalizedResolvedPlaceId,
                        oldStars,
                        stars);
                syncRatingToSearch(updatedPlace);
            }
            return mapToReviewResponseDTO(persisted);
        }

        PlaceReview review = new PlaceReview();
        review.setUserId(resolvedUserId);
        review.setPlaceId(normalizedResolvedPlaceId);
        review.setStars(stars);
        review.setComment(normalizedComment);
        review.setExternalSource(normalizeNullable(externalSource));
        review.setExternalId(normalizeNullable(externalId));
        review.setLat(lat);
        review.setLng(lng);
        review.setPlaceName(normalizeNullable(placeName));
        review.setCreatedAt(Instant.now());

        PlaceReview persisted;
        try {
            persisted = ratingRepository.save(review);
            Place updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
                    resolvedUserId,
                    normalizedResolvedPlaceId,
                    stars);
                syncRatingToSearch(updatedPlace);
            return mapToReviewResponseDTO(persisted);
        } catch (DuplicateKeyException ex) {
            Optional<PlaceReview> concurrent = ratingRepository
                    .findByUserIdAndPlaceId(resolvedUserId, normalizedResolvedPlaceId);
            if (concurrent.isEmpty()) {
                throw new DuplicateKeyException(
                        "Review already exists for user " + resolvedUserId
                            + " and place " + normalizedResolvedPlaceId,
                        ex);
            }

            PlaceReview existingReview = concurrent.get();
            int oldStars = existingReview.getStars();
            existingReview.setStars(stars);
            existingReview.setComment(normalizedComment);
            existingReview.setExternalSource(normalizeNullable(externalSource));
            existingReview.setExternalId(normalizeNullable(externalId));
            existingReview.setLat(lat);
            existingReview.setLng(lng);
            existingReview.setPlaceName(normalizeNullable(placeName));
            if (existingReview.getCreatedAt() == null) {
                existingReview.setCreatedAt(Instant.now());
            }

            persisted = ratingRepository.save(existingReview);
            if (oldStars != stars) {
                Place updatedPlace = placeRatingCacheUpdater.replaceAndRecalculate(
                        resolvedUserId,
                        normalizedResolvedPlaceId,
                        oldStars,
                        stars);
                syncRatingToSearch(updatedPlace);
            }
            return mapToReviewResponseDTO(persisted);
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

    public List<PlaceReview> getPlaceReviewsAsList(String placeId) {
        String resolvedPlaceId = required(placeId, "placeId");
        return ratingRepository.findByPlaceIdOrderByCreatedAtDesc(resolvedPlaceId);
    }

    public List<ReviewResponseDTO> getPlaceReviewsWithUsers(String placeId) {
        List<PlaceReview> reviews = getPlaceReviewsAsList(placeId);

        Set<String> userIds = reviews.stream()
            .map(PlaceReview::getUserId)
            .filter(Objects::nonNull)
            .filter(userId -> !userId.isBlank())
            .collect(Collectors.toSet());

        Map<String, String> userNamesById = userIds.isEmpty()
            ? Collections.emptyMap()
            : userRepository.findAllById(userIds).stream()
                .filter(user -> user.getId() != null)
                .filter(user -> user.getUsername() != null && !user.getUsername().isBlank())
                .collect(Collectors.toMap(
                    User::getId,
                    User::getUsername,
                    (first, ignored) -> first));

        return reviews.stream()
            .map(review -> mapToReviewResponseDTO(review, userNamesById))
            .collect(Collectors.toList());
    }

    public Optional<ReviewResponseDTO> getUserReviewWithUser(String userId, String placeId) {
        return getUserReview(userId, placeId).map(this::mapToReviewResponseDTO);
    }

    private ReviewResponseDTO mapToReviewResponseDTO(PlaceReview review) {
        String userName = "Anonymous";
        if (review.getUserId() != null) {
            Optional<User> userOpt = userRepository.findById(review.getUserId());
            if (userOpt.isPresent()) {
                String resolvedName = userOpt.get().getUsername();
                if (resolvedName != null && !resolvedName.isBlank()) {
                    userName = resolvedName;
                }
            }
        }
        return toReviewResponseDTO(review, userName);
    }

    private ReviewResponseDTO mapToReviewResponseDTO(
            PlaceReview review,
            Map<String, String> userNamesById
    ) {
        String userName = "Anonymous";
        if (review.getUserId() != null) {
            String resolvedName = userNamesById.get(review.getUserId());
            if (resolvedName != null && !resolvedName.isBlank()) {
                userName = resolvedName;
            }
        }
        return toReviewResponseDTO(review, userName);
    }

    private ReviewResponseDTO toReviewResponseDTO(PlaceReview review, String userName) {
        long createdAt = review.getCreatedAt() != null ? review.getCreatedAt().toEpochMilli() : 0L;
        return new ReviewResponseDTO(
                review.getId(),
                review.getPlaceId(),
                review.getUserId(),
                userName,
                review.getStars(),
                review.getComment(),
                review.getExternalSource(),
                review.getExternalId(),
                review.getLat(),
                review.getLng(),
                review.getPlaceName(),
                createdAt
        );
    }

    /**
     * Deletes the caller's review and updates the place rating cache sequentially.
     *
     * @throws NoSuchElementException if the review does not exist
     */
    public void deleteReview(String userId, String placeId) {
        String resolvedUserId = required(userId, "userId");
        String resolvedPlaceId = required(placeId, "placeId");

        PlaceReview review = ratingRepository.findByUserIdAndPlaceId(resolvedUserId, resolvedPlaceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Review not found for user " + resolvedUserId + " and place " + resolvedPlaceId));

        ratingRepository.deleteById(review.getId());

        Place updatedPlace = placeRatingCacheUpdater.decrementAndRecalculate(
                resolvedUserId,
                resolvedPlaceId,
                review.getStars());
        syncRatingToSearch(updatedPlace);
    }

    private void syncRatingToSearch(Place updatedPlace) {
        if (updatedPlace == null || updatedPlace.getId() == null || updatedPlace.getId().isBlank()) {
            return;
        }

        try {
            if (placeSearchIndexSyncService instanceof TypesensePlaceIndexSyncService typesenseSync) {
                typesenseSync.updateRatingOnly(
                        updatedPlace.getId(),
                        updatedPlace.getRating(),
                        updatedPlace.getReviewCount());
            } else {
                placeSearchIndexSyncService.upsert(updatedPlace);
            }
        } catch (Exception ex) {
            LOGGER.warn("Rating saved in Mongo but Typesense rating sync failed for place {}",
                    updatedPlace.getId(), ex);
        }
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

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void logPlaceIdCorrectionIfNeeded(String originalPlaceId,
                                              String resolvedPlaceId,
                                              String operation) {
        if (!Objects.equals(originalPlaceId, resolvedPlaceId)) {
            LOGGER.info("{} corrected placeId from {} to {}",
                    operation,
                    originalPlaceId,
                    resolvedPlaceId);
        }
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
