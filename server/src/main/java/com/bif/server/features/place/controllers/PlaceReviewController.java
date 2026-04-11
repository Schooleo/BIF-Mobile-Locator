package com.bif.server.features.place.controllers;

import com.bif.server.common.dto.ErrorDTO;
import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.place.services.RatingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places/{placeId}/reviews")
public class PlaceReviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceReviewController.class);

    private final RatingService ratingService;
    private final PlaceIdentityService placeIdentityService;

    public PlaceReviewController(RatingService ratingService,
                                 PlaceIdentityService placeIdentityService) {
        this.ratingService = ratingService;
        this.placeIdentityService = placeIdentityService;
    }

    @PostMapping
    public ResponseEntity<?> saveReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId,
            @RequestBody @Valid ReviewDTO dto
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String resolvedPlaceId = resolvePlaceIdWithFallback(placeId, dto);
            ReviewResponseDTO saved = ratingService.saveReview(userId, placeId, resolvedPlaceId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorDTO(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDTO(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorDTO(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorDTO(e.getMessage()));
        }
    }

    @GetMapping
    public List<ReviewResponseDTO> getReviews(
            @PathVariable String placeId
    ) {
        return ratingService.getPlaceReviewsWithUsers(placeId);
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMyReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId,
            @RequestBody @Valid ReviewDTO dto
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            String resolvedPlaceId = resolvePlaceIdWithFallback(placeId, dto);
            ReviewResponseDTO saved = ratingService.saveOrUpdateReview(
                    dto.stars(),
                    dto.comment(),
                    userId,
                    placeId,
                    resolvedPlaceId,
                    0L,
                    dto.externalSource(),
                    dto.externalId(),
                    dto.lat(),
                    dto.lng(),
                    dto.placeName());
            return ResponseEntity.ok(saved);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDTO(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorDTO(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorDTO(e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ReviewResponseDTO> getMyReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<ReviewResponseDTO> review = ratingService.getUserReviewWithUser(userId, placeId);
        return review.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMyReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ratingService.deleteReview(userId, placeId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDTO(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorDTO(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorDTO(e.getMessage()));
        }
    }

    private String resolvePlaceIdWithFallback(String originalPlaceId, ReviewDTO dto) {
        if (dto == null
                || isBlank(dto.externalSource())
                || dto.lat() == null
                || dto.lng() == null
                || isBlank(dto.placeName())) {
            LOGGER.warn("Place review API: missing metadata for place resolution, fallback to placeId={}",
                    originalPlaceId);
            return originalPlaceId;
        }

        try {
            String resolvedPlaceId = placeIdentityService.resolveInternalPlaceId(
                    dto.externalSource(),
                    dto.externalId(),
                    dto.lat(),
                    dto.lng(),
                    dto.placeName());

            if (isBlank(resolvedPlaceId)) {
                LOGGER.warn("Place review API: place resolution returned blank, fallback to placeId={}",
                        originalPlaceId);
                return originalPlaceId;
            }

            return resolvedPlaceId;
        } catch (Exception ex) {
            LOGGER.warn("Place review API: place resolution failed, fallback to placeId={}",
                    originalPlaceId,
                    ex);
            return originalPlaceId;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
