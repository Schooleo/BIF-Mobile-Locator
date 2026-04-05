package com.bif.server.features.place.controllers;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.services.RatingService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/places/{placeId}/reviews")
public class PlaceReviewController {
    private final RatingService ratingService;

    public PlaceReviewController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    public ResponseEntity<PlaceReview> saveReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId,
            @RequestBody ReviewDTO dto
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            PlaceReview saved = ratingService.saveReview(userId, placeId, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    public List<ReviewResponseDTO> getReviews(
            @PathVariable String placeId
    ) {
        return ratingService.getPlaceReviewsWithUsers(placeId);
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
}
