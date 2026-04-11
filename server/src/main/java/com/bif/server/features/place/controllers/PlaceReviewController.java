package com.bif.server.features.place.controllers;

import com.bif.server.common.dto.ErrorDTO;
import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.RatingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    public ResponseEntity<?> saveReview(
            @AuthenticationPrincipal String userId,
            @PathVariable String placeId,
            @RequestBody @Valid ReviewDTO dto
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ReviewResponseDTO saved = ratingService.saveReview(userId, placeId, dto);
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
            ReviewResponseDTO saved = ratingService.saveOrUpdateReview(
                    dto.stars(),
                    dto.comment(),
                    userId,
                    placeId,
                    0L);
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
}
