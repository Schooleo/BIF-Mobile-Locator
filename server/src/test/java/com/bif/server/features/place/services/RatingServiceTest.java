package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.events.PlaceRatingUpdatedEvent;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private PlaceRatingCacheUpdater placeRatingCacheUpdater;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(
                ratingRepository,
                placeRatingCacheUpdater,
                applicationEventPublisher);
    }

    @Test
    void saveReview_PersistsReviewAndUpdatesCachedRating() {
        when(ratingRepository.save(any(PlaceReview.class))).thenAnswer(invocation -> {
            PlaceReview review = invocation.getArgument(0);
            review.setId("r1");
            return review;
        });

        Place updatedPlace = new Place();
        updatedPlace.setId("p1");
        when(placeRatingCacheUpdater.incrementAndRecalculate("u1", "p1", 5))
                .thenReturn(updatedPlace);

        PlaceReview saved = ratingService.saveReview("u1", "p1", new ReviewDTO(5, "  Great place  "));

        assertEquals("r1", saved.getId());
        assertEquals("u1", saved.getUserId());
        assertEquals("p1", saved.getPlaceId());
        assertEquals(5, saved.getStars());
        assertEquals("Great place", saved.getComment());

        ArgumentCaptor<PlaceReview> reviewCaptor = ArgumentCaptor.forClass(PlaceReview.class);
        verify(ratingRepository).save(reviewCaptor.capture());
        assertEquals("u1", reviewCaptor.getValue().getUserId());
        assertEquals("p1", reviewCaptor.getValue().getPlaceId());
        assertEquals(5, reviewCaptor.getValue().getStars());

        ArgumentCaptor<PlaceRatingUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PlaceRatingUpdatedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        PlaceRatingUpdatedEvent event = eventCaptor.getValue();
        assertEquals("p1", event.placeId());
        assertEquals(updatedPlace.getRating(), event.rating(), 0.0001);
        assertEquals(updatedPlace.getReviewCount(), event.reviewCount());
    }

    @Test
        void saveReview_WhenDuplicateReview_ThrowsDuplicateKeyException() {
        when(ratingRepository.save(any(PlaceReview.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

                assertThrows(DuplicateKeyException.class,
                () -> ratingService.saveReview("u1", "p1", new ReviewDTO(4, "ok")));

                verifyNoInteractions(placeRatingCacheUpdater, applicationEventPublisher);
    }

    @Test
    void getUserReview_DelegatesToRepository() {
        PlaceReview review = new PlaceReview();
        when(ratingRepository.findByUserIdAndPlaceId("u1", "p1"))
                .thenReturn(Optional.of(review));

        Optional<PlaceReview> result = ratingService.getUserReview("u1", "p1");

        assertTrue(result.isPresent());
        verify(ratingRepository).findByUserIdAndPlaceId("u1", "p1");
    }

    @Test
    void getPlaceReviews_DelegatesToRepository() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(ratingRepository.findByPlaceIdOrderByCreatedAtDesc("p1", pageable))
                .thenReturn(new PageImpl<>(List.of(new PlaceReview())));

        var result = ratingService.getPlaceReviews("p1", pageable);

        assertEquals(1, result.getTotalElements());
        verify(ratingRepository).findByPlaceIdOrderByCreatedAtDesc("p1", pageable);
    }

    @Test
    void deleteReview_WhenFound_DecrementsCacheAndDeletesReview() {
        PlaceReview review = new PlaceReview();
        review.setId("r1");
        review.setUserId("u1");
        review.setPlaceId("p1");
        review.setStars(4);

        when(ratingRepository.findByUserIdAndPlaceId("u1", "p1"))
                .thenReturn(Optional.of(review));

        Place updatedPlace = new Place();
        updatedPlace.setId("p1");
        when(placeRatingCacheUpdater.decrementAndRecalculate("u1", "p1", 4))
                .thenReturn(updatedPlace);

        ratingService.deleteReview("u1", "p1");

        verify(ratingRepository).findByUserIdAndPlaceId("u1", "p1");
        verify(placeRatingCacheUpdater).decrementAndRecalculate("u1", "p1", 4);
        verify(ratingRepository).deleteById("r1");
        ArgumentCaptor<PlaceRatingUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(PlaceRatingUpdatedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        PlaceRatingUpdatedEvent event = eventCaptor.getValue();
        assertEquals("p1", event.placeId());
        assertEquals(updatedPlace.getRating(), event.rating(), 0.0001);
        assertEquals(updatedPlace.getReviewCount(), event.reviewCount());
    }

    @Test
    void deleteReview_WhenReviewNotFound_ThrowsNotFound() {
        when(ratingRepository.findByUserIdAndPlaceId("u1", "p1"))
                .thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> ratingService.deleteReview("u1", "p1"));

        verify(ratingRepository).findByUserIdAndPlaceId("u1", "p1");
        verifyNoInteractions(placeRatingCacheUpdater);
        verify(ratingRepository, never()).deleteById(any());
    }

    @Test
    void deleteReview_WhenCacheUpdateFails_DoesNotDeleteReviewDocument() {
        PlaceReview review = new PlaceReview();
        review.setId("r1");
        review.setStars(5);

        when(ratingRepository.findByUserIdAndPlaceId("u1", "p1"))
                .thenReturn(Optional.of(review));
        when(placeRatingCacheUpdater.decrementAndRecalculate("u1", "p1", 5))
                .thenThrow(new IllegalStateException("concurrent update"));

        assertThrows(IllegalStateException.class,
                () -> ratingService.deleteReview("u1", "p1"));

        verify(ratingRepository, never()).deleteById(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }
}
