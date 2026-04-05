package com.bif.server.features.place.services;

import com.bif.server.features.place.dto.rest.ReviewDTO;
import com.bif.server.features.place.events.PlaceRatingUpdatedEvent;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.RatingRepository;
import com.bif.server.features.sync.services.SyncVersionService;
import com.bif.server.features.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SyncVersionService syncVersionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserRepository userRepository;

    private PlaceRatingCacheUpdater updater;
    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        updater = new PlaceRatingCacheUpdater(mongoTemplate, syncVersionService);
        ratingService = new RatingService(ratingRepository, updater, eventPublisher, userRepository);
    }

    @Test
    void saveReview_WhenAddingNewReview_CalculatesCorrectAtomicMathAndPublishesEvent() {
        String userId = "u1";
        String placeId = "p1";
        ReviewDTO dto = new ReviewDTO(5, "Excellent");

        Place snapshot = new Place();
        snapshot.setId(placeId);
        snapshot.setRating(4.0);
        snapshot.setReviewCount(10);
        
        when(mongoTemplate.findOne(any(Query.class), eq(Place.class))).thenReturn(snapshot);

        Place updatedResult = new Place();
        updatedResult.setId(placeId);
        updatedResult.setRating(4.09);
        updatedResult.setReviewCount(11);
        
        when(mongoTemplate.findAndModify(any(Query.class), any(), any(), eq(Place.class)))
                .thenReturn(updatedResult);
        
        when(ratingRepository.save(any(PlaceReview.class))).thenAnswer(i -> i.getArguments()[0]);

        ratingService.saveReview(userId, placeId, dto);

        ArgumentCaptor<PlaceRatingUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PlaceRatingUpdatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        
        PlaceRatingUpdatedEvent event = eventCaptor.getValue();
        assertEquals(placeId, event.placeId());
        assertEquals(4.09, event.rating(), 0.001);
        assertEquals(11, event.reviewCount());
        
        verify(mongoTemplate).findAndModify(any(Query.class), any(), any(), eq(Place.class));
    }

    @Test
    void deleteReview_WhenRemoved_RecalculatesRatingCorrectly() {
        String userId = "u1";
        String placeId = "p1";
        PlaceReview existingReview = new PlaceReview();
        existingReview.setId("r1");
        existingReview.setStars(5);
        
        when(ratingRepository.findByUserIdAndPlaceId(userId, placeId)).thenReturn(Optional.of(existingReview));

        Place snapshot = new Place();
        snapshot.setId(placeId);
        snapshot.setRating(4.09);
        snapshot.setReviewCount(11);
        when(mongoTemplate.findOne(any(Query.class), eq(Place.class))).thenReturn(snapshot);

        Place updatedResult = new Place();
        updatedResult.setId(placeId);
        updatedResult.setRating(4.0);
        updatedResult.setReviewCount(10);
        
        when(mongoTemplate.findAndModify(any(Query.class), any(), any(), eq(Place.class)))
                .thenReturn(updatedResult);

        ratingService.deleteReview(userId, placeId);

        verify(ratingRepository).deleteById("r1");
        
        ArgumentCaptor<PlaceRatingUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(PlaceRatingUpdatedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        
        PlaceRatingUpdatedEvent event = eventCaptor.getValue();
        assertEquals(4.0, event.rating(), 0.001);
        assertEquals(10, event.reviewCount());
    }
}
