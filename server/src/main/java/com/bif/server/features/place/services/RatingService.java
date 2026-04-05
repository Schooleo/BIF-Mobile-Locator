package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.events.PlaceRatingUpdatedEvent;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;

@Service
public class RatingService {
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

        // Lưu review (Nếu trùng userId+placeId sẽ văng DuplicateKeyException và tự Rollback)
        PlaceReview persistedReview = ratingRepository.save(review);

        // Cập nhật cache (Nếu lỗi ở đây, DB cũng tự Rollback review vừa lưu trên)
        Place updatedPlace = placeRatingCacheUpdater.incrementAndRecalculate(
                resolvedUserId, resolvedPlaceId, dto.stars());

        // Phát sự kiện
        applicationEventPublisher.publishEvent(new PlaceRatingUpdatedEvent(
                updatedPlace.getId(),
                updatedPlace.getRating(),
                updatedPlace.getReviewCount()));

        return persistedReview;
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
        return reviews.stream().map(this::mapToReviewResponseDTO).collect(Collectors.toList());
    }

    public Optional<ReviewResponseDTO> getUserReviewWithUser(String userId, String placeId) {
        return getUserReview(userId, placeId).map(this::mapToReviewResponseDTO);
    }

    private ReviewResponseDTO mapToReviewResponseDTO(PlaceReview review) {
        String userName = "Anonymous";
        if (review.getUserId() != null) {
            Optional<User> userOpt = userRepository.findById(review.getUserId());
            if (userOpt.isPresent() && userOpt.get().getUsername() != null) {
                userName = userOpt.get().getUsername();
            }
        }
        return new ReviewResponseDTO(
                review.getId(),
                review.getPlaceId(),
                review.getUserId(),
                userName,
                review.getStars(),
                review.getComment(),
                review.getCreatedAt()
        );
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
        applicationEventPublisher.publishEvent(new PlaceRatingUpdatedEvent(
            updatedPlace.getId(),
            updatedPlace.getRating(),
            updatedPlace.getReviewCount()));
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
