package com.bif.server.features.place.controllers;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.services.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        ResponseEntity<PlaceReview> result = controller.saveReview("", "p1", new ReviewDTO(5, "ok"));

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void saveReview_WhenValid_ReturnsCreated() {
        PlaceReview saved = new PlaceReview();
        saved.setId("r1");
        when(ratingService.saveReview("u1", "p1", new ReviewDTO(5, "nice"))).thenReturn(saved);

        ResponseEntity<PlaceReview> result = controller.saveReview("u1", "p1", new ReviewDTO(5, "nice"));

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(saved, result.getBody());
    }

    @Test
    void getReviews_UsesPagingParams() {
        when(ratingService.getPlaceReviews(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of(new PlaceReview())));

        var result = controller.getReviews("p1", 2, 10);

        assertEquals(1, result.getTotalElements());

        var captor = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(ratingService).getPlaceReviews(eq("p1"), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void getMyReview_WhenUnauthorized_Returns401() {
        ResponseEntity<PlaceReview> result = controller.getMyReview(null, "p1");

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
    }

    @Test
    void getMyReview_WhenFound_ReturnsOk() {
        PlaceReview review = new PlaceReview();
        when(ratingService.getUserReview("u1", "p1")).thenReturn(Optional.of(review));

        ResponseEntity<PlaceReview> result = controller.getMyReview("u1", "p1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(review, result.getBody());
    }

    @Test
    void getMyReview_WhenMissing_Returns404() {
        when(ratingService.getUserReview("u1", "p1")).thenReturn(Optional.empty());

        ResponseEntity<PlaceReview> result = controller.getMyReview("u1", "p1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void saveReview_WhenServiceRejects_ReturnsConflict() {
        when(ratingService.saveReview("u1", "p1", new ReviewDTO(5, "ok")))
                .thenThrow(new IllegalStateException("duplicate"));

        ResponseEntity<PlaceReview> result = controller.saveReview("u1", "p1", new ReviewDTO(5, "ok"));

        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
    }

    @Test
    void getReviews_ClampsPageAndSize() {
        when(ratingService.getPlaceReviews(eq("p1"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getReviews("p1", -1, 1000);

        var captor = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(ratingService).getPlaceReviews(eq("p1"), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
        assertTrue(captor.getValue().isPaged());
    }
}
