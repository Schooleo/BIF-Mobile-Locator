package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.events.PlaceRatingUpdatedEvent;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

/**
 * Custom exception wrapping both the original cache/search failure and the
 * compensation (undo) failure to provide richer diagnostic information.
 */
class CompensationFailedException extends IllegalStateException {
    private final Exception originalException;
    private final Exception undoException;

    public CompensationFailedException(String message, Exception originalException, Exception undoException) {
        super(message);
        this.originalException = originalException;
        this.undoException = undoException;
        this.initCause(originalException);
    }

    public Exception getOriginalException() {
        return originalException;
    }

    public Exception getUndoException() {
        return undoException;
    }

    @Override
    public String toString() {
        return super.toString()
                + "; originalException=" + originalException
                + "; undoException=" + undoException;
    }
}

@Service
public class RatingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RatingService.class);

    private final RatingRepository ratingRepository;
    private final PlaceRatingCacheUpdater placeRatingCacheUpdater;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UserRepository userRepository;

    public RatingService(RatingRepository ratingRepository,
                         PlaceRatingCacheUpdater placeRatingCacheUpdater,
                         ApplicationEventPublisher applicationEventPublisher,
                         UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.placeRatingCacheUpdater = placeRatingCacheUpdater;
        this.applicationEventPublisher = applicationEventPublisher;
        this.userRepository = userRepository;
    }

    /**
     * Saves a review and updates the place rating cache sequentially.
     * Implements explicit compensation: DB write → try { cache update } catch { undo DB write → rethrow }.
     * Before creating a new review, checks if a review already exists with the resolved placeId.
     * If it exists, merges the DTO fields into it (preserving original createdAt) and deletes the old review.
     *
     * @throws DuplicateKeyException if a review already exists for this user/place
     * @throws CompensationFailedException if cache update fails AND the compensation also fails
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

        // First: Check if review exists with resolved placeId (primary location)
        Optional<PlaceReview> existingByResolvedId = ratingRepository
            .findByUserIdAndPlaceId(resolvedUserId, normalizedResolvedPlaceId);

        PlaceReview review;
        PlaceReview oldReviewToDelete = null;

        if (existingByResolvedId.isPresent()) {
            // Review already exists at resolved location: merge DTO into it
            review = existingByResolvedId.get();
            review.setStars(dto.stars());
            review.setComment(normalizeString(dto.comment()));
            review.setExternalSource(normalizeString(dto.externalSource()));
            review.setExternalId(normalizeString(dto.externalId()));
            review.setLat(dto.lat());
            review.setLng(dto.lng());
            review.setPlaceName(normalizeString(dto.placeName()));
            // Keep original createdAt, do NOT update to server time
        } else {
            // Second: Check if review exists with original placeId (migration scenario)
            Optional<PlaceReview> existingByOriginalId = ratingRepository
                .findByUserIdAndPlaceId(resolvedUserId, normalizedOriginalPlaceId);
            
            if (existingByOriginalId.isPresent()) {
                // Migrate existing review to resolved placeId
                review = existingByOriginalId.get();
                oldReviewToDelete = review;  // Mark for deletion after save
                review.setPlaceId(normalizedResolvedPlaceId);
                // Update other fields from DTO
                review.setStars(dto.stars());
                review.setComment(normalizeString(dto.comment()));
                review.setExternalSource(normalizeString(dto.externalSource()));
                review.setExternalId(normalizeString(dto.externalId()));
                review.setLat(dto.lat());
                review.setLng(dto.lng());
                review.setPlaceName(normalizeString(dto.placeName()));
                // Keep original createdAt, do NOT update to server time
            } else {
                // Third: Create new review
                review = new PlaceReview();
                review.setUserId(resolvedUserId);
                review.setPlaceId(normalizedResolvedPlaceId);
                review.setStars(dto.stars());
                review.setComment(normalizeString(dto.comment()));
                review.setExternalSource(normalizeString(dto.externalSource()));
                review.setExternalId(normalizeString(dto.externalId()));
                review.setLat(dto.lat());
                review.setLng(dto.lng());
                review.setPlaceName(normalizeString(dto.placeName()));
                // Always force server time for new reviews
                review.setCreatedAt(Instant.now());
            }
        }

        PlaceReview persistedReview;
        try {
            LOGGER.info("Saving review candidate userId={} originalPlaceId={} resolvedPlaceId={} stars={} externalSource={} externalId={} lat={} lng={} placeName={}",
                    resolvedUserId,
                    normalizedOriginalPlaceId,
                    normalizedResolvedPlaceId,
                    dto.stars(),
                    dto.externalSource(),
                    dto.externalId(),
                    dto.lat(),
                    dto.lng(),
                    dto.placeName());
            persistedReview = ratingRepository.save(review);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateKeyException(
                "Review already exists for user " + resolvedUserId
                    + " and place " + normalizedResolvedPlaceId,
                ex);
        }

        // If we migrated from old location, delete the old review
        if (oldReviewToDelete != null && !Objects.equals(oldReviewToDelete.getId(), persistedReview.getId())) {
            try {
                ratingRepository.deleteById(oldReviewToDelete.getId());
                LOGGER.info("Migrated review from {} to {}", normalizedOriginalPlaceId, normalizedResolvedPlaceId);
            } catch (Exception deleteEx) {
                LOGGER.warn("Warning: Failed to delete old review {} during migration",
                    oldReviewToDelete.getId(), deleteEx);
            }
        }

        // Compensation block: DB write succeeded, now try cache/search updates.
        try {
            Place updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
                resolvedUserId, normalizedResolvedPlaceId, dto.stars());
            syncRatingToSearch(updatedPlace);
        } catch (Exception ex) {
            // Undo: Delete the persisted review since cache update failed
            Exception undoException = null;
            try {
                ratingRepository.deleteById(persistedReview.getId());
                LOGGER.warn("Compensation: Deleted review {} after cache/search update failure", persistedReview.getId());
            } catch (Exception undoEx) {
                undoException = undoEx;
                LOGGER.error("Failed to undo review save (delete) after cache/search update failure for review {}",
                    persistedReview.getId(), undoEx);
            }
            
            if (undoException != null) {
                throw new CompensationFailedException(
                    "Cache/search update failed for review " + persistedReview.getId()
                        + ", and subsequent undo (delete) operation also failed",
                    ex,
                    undoException);
            }
            throw ex;
        }

        return mapToReviewResponseDTO(persistedReview);
    }  

    /**
     * Saves or updates a review and updates the place rating cache sequentially.
     * Handles three scenarios:
     * 1. Existing review update - calls replaceAndRecalculate
     * 2. New review create - calls incrementAndRecalculate
     * 3. Concurrent race condition - retries with updated state
     * Implements explicit compensation: DB write → try { cache update } catch { undo DB write → rethrow }.
     * Before creating a new review, checks if a review already exists with the resolved placeId.
     *
     * @throws CompensationFailedException if cache update fails AND the compensation also fails
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

        String normalizedComment = normalizeString(comment);

        // First: Check if review exists with resolved placeId
        Optional<PlaceReview> existingOpt = ratingRepository
            .findByUserIdAndPlaceId(resolvedUserId, normalizedResolvedPlaceId);

        if (existingOpt.isPresent()) {
            PlaceReview existingReview = existingOpt.get();
            int oldStars = existingReview.getStars();
            existingReview.setStars(stars);
            existingReview.setComment(normalizedComment);
            existingReview.setExternalSource(normalizeString(externalSource));
            existingReview.setExternalId(normalizeString(externalId));
            existingReview.setLat(lat);
            existingReview.setLng(lng);
            existingReview.setPlaceName(normalizeString(placeName));
            if (existingReview.getCreatedAt() == null) {
                existingReview.setCreatedAt(Instant.now());
            }

            PlaceReview persisted = ratingRepository.save(existingReview);
            // Compensation: DB write succeeded, now try cache/search updates.
            try {
                if (oldStars != stars) {
                    Place updatedPlace = placeRatingCacheUpdater.replaceAndRecalculate(
                            resolvedUserId,
                            normalizedResolvedPlaceId,
                            oldStars,
                            stars);
                    syncRatingToSearch(updatedPlace);
                }
            } catch (Exception ex) {
                // Undo: Restore the old star rating since cache update failed
                Exception undoException = null;
                try {
                    existingReview.setStars(oldStars);
                    ratingRepository.save(existingReview);
                    LOGGER.warn("Compensation: Restored review {} to oldStars {} after cache/search update failure",
                        existingReview.getId(), oldStars);
                } catch (Exception undoEx) {
                    undoException = undoEx;
                    LOGGER.error("Failed to undo review update (restore oldStars) after cache/search update failure for review {}",
                        existingReview.getId(), undoEx);
                }
                
                if (undoException != null) {
                    throw new CompensationFailedException(
                        "Cache/search update failed for review " + existingReview.getId()
                            + ", and subsequent undo (restore stars) operation also failed",
                        ex,
                        undoException);
                }
                throw ex;
            }
            return mapToReviewResponseDTO(persisted);
        }

        // Check if review exists with original placeId (migration scenario)
        Optional<PlaceReview> existingByOriginalId = ratingRepository
            .findByUserIdAndPlaceId(resolvedUserId, normalizedOriginalPlaceId);

        PlaceReview review;
        PlaceReview oldReviewToDelete = null;

        if (existingByOriginalId.isPresent()) {
            // Migrate existing review from original to resolved placeId
            review = existingByOriginalId.get();
            oldReviewToDelete = review;
            review.setPlaceId(normalizedResolvedPlaceId);
            review.setStars(stars);
            review.setComment(normalizedComment);
            review.setExternalSource(normalizeString(externalSource));
            review.setExternalId(normalizeString(externalId));
            review.setLat(lat);
            review.setLng(lng);
            review.setPlaceName(normalizeString(placeName));
            if (review.getCreatedAt() == null) {
                review.setCreatedAt(Instant.now());
            }
        } else {
            // Create new review
            review = new PlaceReview();
            review.setUserId(resolvedUserId);
            review.setPlaceId(normalizedResolvedPlaceId);
            review.setStars(stars);
            review.setComment(normalizedComment);
            review.setExternalSource(normalizeString(externalSource));
            review.setExternalId(normalizeString(externalId));
            review.setLat(lat);
            review.setLng(lng);
            review.setPlaceName(normalizeString(placeName));
            review.setCreatedAt(Instant.now());
        }

        PlaceReview persisted;
        try {
            persisted = ratingRepository.save(review);
        } catch (DuplicateKeyException ex) {
            // Race condition: Another thread created the review between our check and save.
            // Load it and attempt update instead.
            Optional<PlaceReview> concurrent = ratingRepository
                    .findByUserIdAndPlaceId(resolvedUserId, normalizedResolvedPlaceId);
            if (concurrent.isEmpty()) {
                throw new DuplicateKeyException(
                        "Review already exists for user " + resolvedUserId
                            + " and place " + normalizedResolvedPlaceId,
                        ex);
            }

            PlaceReview concurrentReview = concurrent.get();
            int oldStars = concurrentReview.getStars();
            concurrentReview.setStars(stars);
            concurrentReview.setComment(normalizedComment);
            concurrentReview.setExternalSource(normalizeString(externalSource));
            concurrentReview.setExternalId(normalizeString(externalId));
            concurrentReview.setLat(lat);
            concurrentReview.setLng(lng);
            concurrentReview.setPlaceName(normalizeString(placeName));
            if (concurrentReview.getCreatedAt() == null) {
                concurrentReview.setCreatedAt(Instant.now());
            }

            persisted = ratingRepository.save(concurrentReview);
            // Compensation: DB write succeeded, now try cache/search updates.
            try {
                if (oldStars != stars) {
                    Place updatedPlace = placeRatingCacheUpdater.replaceAndRecalculate(
                            resolvedUserId,
                            normalizedResolvedPlaceId,
                            oldStars,
                            stars);
                    syncRatingToSearch(updatedPlace);
                }
            } catch (Exception cacheEx) {
                // Undo: Restore the old star rating since cache update failed
                Exception undoException = null;
                try {
                    concurrentReview.setStars(oldStars);
                    ratingRepository.save(concurrentReview);
                    LOGGER.warn("Compensation: Restored review {} to oldStars {} after cache/search update failure in race condition",
                        concurrentReview.getId(), oldStars);
                } catch (Exception undoEx) {
                    undoException = undoEx;
                    LOGGER.error("Failed to undo review update (restore oldStars) after cache/search update failure in race condition for review {}",
                        concurrentReview.getId(), undoEx);
                }
                
                if (undoException != null) {
                    throw new CompensationFailedException(
                        "Cache/search update failed for review " + concurrentReview.getId()
                            + " in race condition, and subsequent undo (restore stars) operation also failed",
                        cacheEx,
                        undoException);
                }
                throw cacheEx;
            }
            return mapToReviewResponseDTO(persisted);
        }

        // If we migrated from old location, delete the old review
        if (oldReviewToDelete != null && !Objects.equals(oldReviewToDelete.getId(), persisted.getId())) {
            try {
                ratingRepository.deleteById(oldReviewToDelete.getId());
                LOGGER.info("Migrated review from {} to {}", normalizedOriginalPlaceId, normalizedResolvedPlaceId);
            } catch (Exception deleteEx) {
                LOGGER.warn("Warning: Failed to delete old review {} during migration",
                    oldReviewToDelete.getId(), deleteEx);
            }
        }

        // New review created or old one migrated: Compensation block for cache/search update.
        try {
            int oldStars = 0;  // For newly created reviews (not migrated)
            if (existingByOriginalId.isPresent() && oldReviewToDelete != null) {
                oldStars = oldReviewToDelete.getStars();
            }
            
            Place updatedPlace;
            if (oldStars > 0) {
                // Migrated review: replace old stars with new stars
                updatedPlace = placeRatingCacheUpdater.replaceAndRecalculate(
                        resolvedUserId,
                        normalizedResolvedPlaceId,
                        oldStars,
                        stars);
            } else {
                // New review: increment
                updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
                        resolvedUserId,
                        normalizedResolvedPlaceId,
                        stars);
            }
            syncRatingToSearch(updatedPlace);
        } catch (Exception ex) {
            // Undo: Delete the persisted review since cache update failed
            Exception undoException = null;
            try {
                ratingRepository.deleteById(persisted.getId());
                LOGGER.warn("Compensation: Deleted review {} after cache/search update failure", persisted.getId());
            } catch (Exception undoEx) {
                undoException = undoEx;
                LOGGER.error("Failed to undo review save (delete) after cache/search update failure for review {}",
                    persisted.getId(), undoEx);
            }
            
            if (undoException != null) {
                throw new CompensationFailedException(
                    "Cache/search update failed for review " + persisted.getId()
                        + ", and subsequent undo (delete) operation also failed",
                    ex,
                    undoException);
            }
            throw ex;
        }
        return mapToReviewResponseDTO(persisted);
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
     * Implements explicit compensation: DB delete → try { cache update } catch { restore review → rethrow }.
     *
     * @throws NoSuchElementException if the review does not exist
     */
    public void deleteReview(String userId, String placeId) {
        String resolvedUserId = required(userId, "userId");
        String resolvedPlaceId = required(placeId, "placeId");

        PlaceReview review = ratingRepository.findByUserIdAndPlaceId(resolvedUserId, resolvedPlaceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Review not found for user " + resolvedUserId + " and place " + resolvedPlaceId));

        // Create a snapshot for potential restoration
        PlaceReview reviewSnapshot = new PlaceReview();
        reviewSnapshot.setId(review.getId());
        reviewSnapshot.setUserId(review.getUserId());
        reviewSnapshot.setPlaceId(review.getPlaceId());
        reviewSnapshot.setStars(review.getStars());
        reviewSnapshot.setComment(review.getComment());
        reviewSnapshot.setExternalSource(review.getExternalSource());
        reviewSnapshot.setExternalId(review.getExternalId());
        reviewSnapshot.setLat(review.getLat());
        reviewSnapshot.setLng(review.getLng());
        reviewSnapshot.setPlaceName(review.getPlaceName());
        reviewSnapshot.setCreatedAt(review.getCreatedAt());

        ratingRepository.deleteById(review.getId());

        // Compensation: DB delete succeeded, now try cache/search updates.
        try {
            Place updatedPlace = placeRatingCacheUpdater.decrementAndRecalculate(
                    resolvedUserId,
                    resolvedPlaceId,
                    review.getStars());
            syncRatingToSearch(updatedPlace);
        } catch (Exception ex) {
            // Undo: Restore the deleted review since cache update failed
            try {
                ratingRepository.save(reviewSnapshot);
                LOGGER.warn("Compensation: Restored deleted review {} after cache update failure", review.getId());
            } catch (Exception undoEx) {
                LOGGER.error("Failed to undo review delete (restore) after cache update failure for review {}",
                    review.getId(), undoEx);
            }
            throw ex;
        }
    }

    private void syncRatingToSearch(Place updatedPlace) {
        if (updatedPlace == null || updatedPlace.getId() == null || updatedPlace.getId().isBlank()) {
            return;
        }

        try {
            PlaceRatingUpdatedEvent event = new PlaceRatingUpdatedEvent(
                    updatedPlace.getId(),
                    updatedPlace.getRating(),
                    updatedPlace.getReviewCount());

            if (TransactionSynchronizationManager.isSynchronizationActive()
                    && TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        applicationEventPublisher.publishEvent(event);
                    }
                });
                return;
            }

            applicationEventPublisher.publishEvent(event);
        } catch (Exception ex) {
            LOGGER.warn("Rating saved in Mongo but rating-sync event publishing failed for place {}",
                    updatedPlace.getId(), ex);
        }
    }

    private void validateStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("stars must be between 1 and 5");
        }
    }

    private String normalizeString(String value) {
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
