package com.bif.server.features.sync.services;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.RatingService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class ReviewSyncEntityHandler implements SyncEntityHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewSyncEntityHandler.class);
    private final RatingService ratingService;
    private final ObjectMapper objectMapper;

    public ReviewSyncEntityHandler(RatingService ratingService,
                                   ObjectMapper objectMapper) {
        this.ratingService = ratingService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return "review";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId, long newVersion) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Review sync: missing authenticated userId");
        }

        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "CREATE";

        if (!"CREATE".equals(operation) && !"UPDATE".equals(operation) && !"DELETE".equals(operation)) {
            throw new IllegalArgumentException("Review sync: unsupported operation '" + operation + "'");
        }

        // entityId format: "placeId:userId"
        String[] parts = pushed.getEntityId() != null
                ? pushed.getEntityId().split(":")
                : new String[0];
        if (parts.length < 2) {
            throw new IllegalArgumentException("Review sync: malformed entityId '" + pushed.getEntityId() + "'");
        }
        String placeId = parts[0];
        String entityUserId = parts[1];

        if (placeId == null || placeId.isBlank()) {
            throw new IllegalArgumentException("Review sync: blank placeId in entityId");
        }
        if (entityUserId == null || entityUserId.isBlank()) {
            throw new IllegalArgumentException("Review sync: blank userId in entityId");
        }

        if (!userId.equals(entityUserId)) {
            throw new IllegalArgumentException(
                "Review sync: entity userId does not match authenticated userId");
        }
        String reviewUserId = userId;

        if ("DELETE".equals(operation)) {
            Optional<ReviewResponseDTO> existing = ratingService.getUserReviewWithUser(reviewUserId, placeId);
            if (existing.isPresent()) {
                ratingService.deleteReview(reviewUserId, placeId);
                LOGGER.debug("Review sync: deleted review for place={}", placeId);
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
        if (payload == null) {
            throw new IllegalArgumentException("Review sync: invalid review payload");
        }

        int stars = payload.rating > 0 ? payload.rating : payload.stars;
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Review sync: stars/rating must be between 1 and 5");
        }
        String comment = payload.comment;
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Review sync: comment cannot be empty");
        }

        ReviewResponseDTO savedReview = ratingService.saveOrUpdateReview(
                stars,
                comment,
                reviewUserId,
                placeId,
                newVersion);

        LOGGER.debug("Review sync: saved review for place={} stars={}", placeId, stars);

        ReviewPayload responsePayload = toPayload(savedReview);
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

        String placeId = parts[0].trim();
        String reviewUserId = parts[1].trim();
        if (placeId.isEmpty() || reviewUserId.isEmpty()) {
            return null;
        }

        Optional<ReviewResponseDTO> reviewOpt = ratingService
            .getUserReviewWithUser(reviewUserId, placeId);
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
            LOGGER.warn("Review sync: failed to write payload", e);
            return null;
        }
    }

    private ReviewPayload toPayload(ReviewResponseDTO review) {
        ReviewPayload payload = new ReviewPayload();
        payload.placeId = review.placeId();
        payload.userId = review.userId();
        payload.userName = review.userName() != null && !review.userName().isBlank()
                ? review.userName()
                : "Anonymous";

        payload.stars = review.stars();
        payload.rating = review.stars();
        payload.comment = review.comment();
        payload.createdAt = review.createdAt() != null
                ? review.createdAt().toString()
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
