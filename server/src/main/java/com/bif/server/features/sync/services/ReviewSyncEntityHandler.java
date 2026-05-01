package com.bif.server.features.sync.services;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.place.services.RatingService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.place.repositories.RatingRepository;
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
    private final PlaceIdentityService placeIdentityService;
    private final ObjectMapper objectMapper;
    private final RatingRepository ratingRepository;

    public ReviewSyncEntityHandler(RatingService ratingService,
                                   PlaceIdentityService placeIdentityService,
                                   ObjectMapper objectMapper,
                                   RatingRepository ratingRepository) {
        this.ratingService = ratingService;
        this.placeIdentityService = placeIdentityService;
        this.objectMapper = objectMapper;
        this.ratingRepository = ratingRepository;
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

        String originalPlaceId = null;
        String entityUserId = null;
        String incomingEntityId = pushed.getEntityId();
        boolean parsedFromCompound = false;

        if (incomingEntityId != null && (incomingEntityId.contains(":") || incomingEntityId.contains("|"))) {
            String[] parts = incomingEntityId.split("[:|]");
            if (parts.length >= 2) {
                originalPlaceId = parts[0];
                entityUserId = parts[1];
                parsedFromCompound = true;
            }
        }

        if (!parsedFromCompound) {
            // Try payload first
            ReviewPayload payloadFromBody = parsePayload(pushed.getPayload());
            if (payloadFromBody != null && !isBlank(payloadFromBody.placeId) && !isBlank(payloadFromBody.userId)) {
                originalPlaceId = payloadFromBody.placeId;
                entityUserId = payloadFromBody.userId;
            } else if (incomingEntityId != null && ratingRepository != null) {
                // Last resort: lookup review by id in DB
                try {
                    var opt = ratingRepository.findById(incomingEntityId);
                    if (opt.isPresent()) {
                        var review = opt.get();
                        originalPlaceId = review.getPlaceId();
                        entityUserId = review.getUserId();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (originalPlaceId == null || originalPlaceId.isBlank()) {
            throw new IllegalArgumentException("Review sync: blank placeId in entityId");
        }
        if (entityUserId == null || entityUserId.isBlank()) {
            throw new IllegalArgumentException("Review sync: blank userId in entityId");
        }
        if (!userId.equals(entityUserId)) {
            throw new IllegalArgumentException("Review sync: entity userId does not match authenticated userId");
        }

        String reviewUserId = userId;

        if ("DELETE".equals(operation)) {
            // Prefer delete by user+place, but if incoming entityId was a review id, resolve by id then delete
            if (!isBlank(incomingEntityId) && ratingRepository != null && !parsedFromCompound) {
                try {
                    var opt = ratingRepository.findById(incomingEntityId);
                    if (opt.isPresent()) {
                        var review = opt.get();
                        ratingService.deleteReview(review.getUserId(), review.getPlaceId());
                        LOGGER.debug("Review sync: deleted review by id={}", incomingEntityId);
                    }
                } catch (Exception ex) {
                    LOGGER.warn("Review sync: delete by id failed, falling back to user+place delete", ex);
                }
            } else {
                Optional<ReviewResponseDTO> existing = ratingService.getUserReviewWithUser(reviewUserId, originalPlaceId);
                if (existing.isPresent()) {
                    ratingService.deleteReview(reviewUserId, originalPlaceId);
                    LOGGER.debug("Review sync: deleted review for place={}", originalPlaceId);
                }
            }

            ReviewPayload response = new ReviewPayload();
            response.placeId = originalPlaceId;
            response.userId = reviewUserId;
            response.deleted = true;
            response.serverVersion = newVersion;
            return writePayload(response);
        }

        ReviewPayload payload = parsePayload(pushed.getPayload());
        if (payload == null) {
            throw new IllegalArgumentException("Review sync: invalid review payload");
        }

        int stars = payload.stars;
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Review sync: stars must be between 1 and 5");
        }

        String resolvedPlaceId = resolvePlaceIdWithFallback(originalPlaceId, payload);

        ReviewResponseDTO savedReview = ratingService.saveOrUpdateReview(
                stars,
                payload.comment,
                reviewUserId,
                originalPlaceId,
                resolvedPlaceId,
                newVersion,
                payload.externalSource,
                payload.externalId,
                payload.lat,
                payload.lng,
                payload.placeName);

        LOGGER.debug("Review sync: saved review for place={} resolvedPlace={} stars={}",
            originalPlaceId,
            resolvedPlaceId,
            stars);

        ReviewPayload responsePayload = toPayload(savedReview);
        responsePayload.placeId = resolvedPlaceId;
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            return entry.getPayload();
        }

        String incomingEntityId = entry.getEntityId();
        if (incomingEntityId == null || incomingEntityId.isBlank()) {
            return null;
        }

        // Legacy place:user entityId
        if (incomingEntityId.contains(":" ) || incomingEntityId.contains("|")) {
            String[] parts = incomingEntityId.split("[:|]");
            if (parts.length < 2) {
                return null;
            }
            String placeId = parts[0].trim();
            String reviewUserId = parts[1].trim();
            if (placeId.isEmpty() || reviewUserId.isEmpty()) {
                return null;
            }
            Optional<ReviewResponseDTO> reviewOpt = ratingService.getUserReviewWithUser(reviewUserId, placeId);
            if (reviewOpt.isEmpty()) {
                return null;
            }
            ReviewPayload payload = toPayload(reviewOpt.get());
            payload.serverVersion = entry.getServerVersion();
            return writePayload(payload);
        }

        // New style: review id
        if (ratingRepository != null) {
            try {
                var opt = ratingRepository.findById(incomingEntityId);
                if (opt.isPresent()) {
                    var r = opt.get();
                    ReviewResponseDTO dto = new ReviewResponseDTO(
                            r.getId(),
                            r.getPlaceId(),
                            r.getUserId(),
                            null,
                            r.getStars(),
                            r.getComment(),
                            r.getCreatedAt() != null ? r.getCreatedAt().toEpochMilli() : 0L
                    );
                    ReviewPayload payload = toPayload(dto);
                    payload.serverVersion = entry.getServerVersion();
                    return writePayload(payload);
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private String resolvePlaceIdWithFallback(String originalPlaceId, ReviewPayload payload) {
        if (payload == null
                || isBlank(payload.externalSource)
                || payload.lat == null
                || payload.lng == null
                || isBlank(payload.placeName)) {
            LOGGER.warn("Review sync: missing metadata for place resolution, fallback to placeId={}", originalPlaceId);
            return originalPlaceId;
        }

        try {
            String resolvedPlaceId = placeIdentityService.resolveInternalPlaceId(
                    payload.externalSource,
                    payload.externalId,
                    payload.lat,
                    payload.lng,
                    payload.placeName);

            if (isBlank(resolvedPlaceId)) {
                LOGGER.warn("Review sync: place resolution returned blank, fallback to placeId={}", originalPlaceId);
                return originalPlaceId;
            }

            return resolvedPlaceId;
        } catch (Exception ex) {
            LOGGER.warn("Review sync: place resolution failed, fallback to placeId={}", originalPlaceId, ex);
            return originalPlaceId;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
        payload.comment = review.comment();
        payload.externalSource = review.externalSource();
        payload.externalId = review.externalId();
        payload.lat = review.lat();
        payload.lng = review.lng();
        payload.placeName = review.placeName();
        payload.createdAt = review.createdAt();
        return payload;
    }

    private static class ReviewPayload {
        public String placeId;
        public String userId;
        public String userName;
        public int stars;
        public String comment;
        public String externalSource;
        public String externalId;
        public Double lat;
        public Double lng;
        public String placeName;
        public long createdAt;
        public long serverVersion;
        public boolean deleted;
    }
}
