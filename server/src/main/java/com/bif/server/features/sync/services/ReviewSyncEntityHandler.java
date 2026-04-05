package com.bif.server.features.sync.services;

import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Component
public class ReviewSyncEntityHandler implements SyncEntityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewSyncEntityHandler.class);
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReviewSyncEntityHandler(RatingRepository ratingRepository, UserRepository userRepository) {
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
    }

    @Override
    public String entityType() {
        return "review";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId, long newVersion) {
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "CREATE";

        // entityId format: "placeId:userId"
        String[] parts = pushed.getEntityId() != null
                ? pushed.getEntityId().split(":")
                : new String[0];
        String placeId = parts.length >= 1 ? parts[0] : null;
        String reviewUserId = parts.length >= 2 ? parts[1] : userId;

        if (placeId == null || placeId.isBlank()) {
            LOGGER.warn("Review sync: missing placeId in entityId '{}'", pushed.getEntityId());
            return pushed.getPayload();
        }

        if ("DELETE".equals(operation)) {
            Optional<PlaceReview> existing = ratingRepository
                    .findByUserIdAndPlaceId(reviewUserId, placeId);
            if (existing.isPresent()) {
                ratingRepository.deleteById(existing.get().getId());
                LOGGER.info("Review sync: deleted review for user={} place={}", reviewUserId, placeId);
            }
            ReviewPayload response = new ReviewPayload();
            response.placeId = placeId;
            response.userId = reviewUserId;
            response.deleted = true;
            response.serverVersion = newVersion;
            return writePayload(response);
        }

        // CREATE or UPDATE
        ReviewPayload payload = parsePayload(pushed.getPayload());

        int stars = 3;
        String comment = null;
        if (payload != null) {
            stars = payload.rating > 0 ? payload.rating : payload.stars;
            comment = payload.comment;
            if (payload.placeId != null && !payload.placeId.isBlank()) {
                placeId = payload.placeId;
            }
            if (payload.userId != null && !payload.userId.isBlank()) {
                reviewUserId = payload.userId;
            }
        }

        // Validate stars
        if (stars < 1) stars = 1;
        if (stars > 5) stars = 5;

        Optional<PlaceReview> existing = ratingRepository
                .findByUserIdAndPlaceId(reviewUserId, placeId);

        PlaceReview review;
        if (existing.isPresent()) {
            review = existing.get();
        } else {
            review = new PlaceReview();
            review.setUserId(reviewUserId);
            review.setPlaceId(placeId);
            review.setCreatedAt(LocalDateTime.now());
        }
        review.setStars(stars);
        review.setComment(comment);
        ratingRepository.save(review);

        LOGGER.info("Review sync: saved review for user={} place={} stars={}", reviewUserId, placeId, stars);

        ReviewPayload responsePayload = toPayload(review);
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            return entry.getPayload();
        }

        String[] parts = entry.getEntityId() != null
                ? entry.getEntityId().split(":")
                : new String[0];
        if (parts.length < 2) {
            return null;
        }

        Optional<PlaceReview> reviewOpt = ratingRepository
                .findByUserIdAndPlaceId(parts[1], parts[0]);
        if (reviewOpt.isEmpty()) {
            return null;
        }

        ReviewPayload payload = toPayload(reviewOpt.get());
        payload.serverVersion = entry.getServerVersion();
        return writePayload(payload);
    }

    private ReviewPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ReviewPayload.class);
        } catch (Exception e) {
            LOGGER.warn("Review sync: failed to parse payload", e);
            return null;
        }
    }

    private String writePayload(ReviewPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private ReviewPayload toPayload(PlaceReview review) {
        ReviewPayload payload = new ReviewPayload();
        payload.placeId = review.getPlaceId();
        payload.userId = review.getUserId();
        
        String userName = "Anonymous";
        if (review.getUserId() != null) {
            Optional<User> userOpt = userRepository.findById(review.getUserId());
            if (userOpt.isPresent() && userOpt.get().getUsername() != null) {
                userName = userOpt.get().getUsername();
            }
        }
        payload.userName = userName;
        
        payload.stars = review.getStars();
        payload.rating = review.getStars();
        payload.comment = review.getComment();
        payload.createdAt = review.getCreatedAt() != null
                ? review.getCreatedAt().toString()
                : null;
        return payload;
    }

    private static class ReviewPayload {
        public String placeId;
        public String userId;
        public String userName;
        public int stars;
        public int rating;
        public String comment;
        public String createdAt;
        public long serverVersion;
        public boolean deleted;
    }
}
