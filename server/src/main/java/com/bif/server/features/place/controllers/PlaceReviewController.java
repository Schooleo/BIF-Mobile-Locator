package com.bif.server.features.place.controllers;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.services.RatingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public Page<PlaceReview> getReviews(
            @PathVariable String placeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);
        return ratingService.getPlaceReviews(placeId, pageable);
    }

    @GetMapping("/me")
    public ResponseEntity<PlaceReview> getMyReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<PlaceReview> review = ratingService.getUserReview(userId, placeId);
        return review.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
