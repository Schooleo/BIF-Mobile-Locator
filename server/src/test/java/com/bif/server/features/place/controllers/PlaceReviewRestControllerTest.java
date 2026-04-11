package com.bif.server.features.place.controllers;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceReviewControllerTest {

    @Mock
    private RatingService ratingService;

    private PlaceReviewController controller;

    @BeforeEach
    void setUp() {
        controller = new PlaceReviewController(ratingService);
    }

    @Test
    void saveReview_WhenUnauthorized_Returns401() {
        ResponseEntity<?> result = controller.saveReview("", "p1", new ReviewDTO(5, "ok"));

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void saveReview_WhenValid_ReturnsCreated() {
        ReviewResponseDTO saved = new ReviewResponseDTO("r1", "p1", "u1", "Anonymous", 5, "nice", 1764547200000L);
        when(ratingService.saveReview("u1", "p1", new ReviewDTO(5, "nice"))).thenReturn(saved);

        ResponseEntity<?> result = controller.saveReview("u1", "p1", new ReviewDTO(5, "nice"));

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(saved, result.getBody());
    }

    @Test
    void getReviews_ReturnsList() {
        ReviewResponseDTO dto = new ReviewResponseDTO("r1", "p1", "u1", "Anonymous", 5, "good", 1764547200000L);
        when(ratingService.getPlaceReviewsWithUsers("p1")).thenReturn(List.of(dto));

        var result = controller.getReviews("p1");

        assertEquals(1, result.size());
        assertEquals("r1", result.get(0).id());
    }

    @Test
    void getMyReview_WhenUnauthorized_Returns401() {
        ResponseEntity<ReviewResponseDTO> result = controller.getMyReview(null, "p1");

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void getMyReview_WhenFound_ReturnsOk() {
        ReviewResponseDTO dto = new ReviewResponseDTO("r1", "p1", "u1", "Me", 5, "great", 1764547200000L);
        when(ratingService.getUserReviewWithUser("u1", "p1")).thenReturn(Optional.of(dto));

        ResponseEntity<ReviewResponseDTO> result = controller.getMyReview("u1", "p1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(dto, result.getBody());
    }

    @Test
    void getMyReview_WhenMissing_Returns404() {
        when(ratingService.getUserReviewWithUser("u1", "p1")).thenReturn(Optional.empty());

        ResponseEntity<ReviewResponseDTO> result = controller.getMyReview("u1", "p1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void saveReview_WhenServiceRejects_ReturnsConflict() {
        when(ratingService.saveReview("u1", "p1", new ReviewDTO(5, "ok")))
                .thenThrow(new IllegalStateException("duplicate"));

        ResponseEntity<?> result = controller.saveReview("u1", "p1", new ReviewDTO(5, "ok"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    @Test
    void saveReview_WhenDuplicateKeyException_ReturnsConflict() {
        when(ratingService.saveReview("u1", "p1", new ReviewDTO(5, "ok")))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        ResponseEntity<?> result = controller.saveReview("u1", "p1", new ReviewDTO(5, "ok"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    @Test
    void updateMyReview_WhenValid_ReturnsOk() {
        ReviewResponseDTO saved = new ReviewResponseDTO("r1", "p1", "u1", "Anonymous", 4, null, 1764547200000L);
        when(ratingService.saveOrUpdateReview(4, null, "u1", "p1", 0L,
            null, null, null, null, null)).thenReturn(saved);

        ResponseEntity<?> result = controller.updateMyReview("u1", "p1", new ReviewDTO(4, null));

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(saved, result.getBody());
    }

    @Test
    void deleteMyReview_WhenValid_ReturnsNoContent() {
        ResponseEntity<?> result = controller.deleteMyReview("u1", "p1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(ratingService).deleteReview(eq("u1"), eq("p1"));
    }

    // paging test removed because getReviews no longer utilizes paging parameters
}
