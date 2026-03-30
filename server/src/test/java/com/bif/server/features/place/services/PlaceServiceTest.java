package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceAddressEnrichmentService;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.PlaceSearchProvider;
import com.bif.server.features.sync.services.SyncVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private SyncVersionService syncVersionService;

    @Mock
    private PlaceAddressEnrichmentService placeAddressEnrichmentService;

    @Mock
    private PlaceSearchProvider placeSearchProvider;

    @Mock
    private PlaceSearchIndexSyncService placeSearchIndexSyncService;

    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = new PlaceService(placeRepository,
                syncVersionService,
            placeAddressEnrichmentService,
            placeSearchProvider,
            placeSearchIndexSyncService,
            "osm_geocoder");
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        Place place = new Place();
        when(placeRepository.findAll()).thenReturn(List.of(place));

        List<Place> result = placeService.getAll();

        assertEquals(1, result.size());
        verify(placeRepository).findAll();
    }

    @Test
    void getById_ReturnsOptional() {
        Place place = new Place();
        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));

        Optional<Place> result = placeService.getById("p1");

        assertTrue(result.isPresent());
        verify(placeRepository).findById("p1");
    }

    @Test
    void save_StampsVersionAndPersists() {
        Place place = new Place();
        place.setPersistedByUserId("user1");
        when(placeAddressEnrichmentService.enrichAddress(any(), any(), any()))
            .thenReturn("123 Main St");
        when(syncVersionService.nextVersion()).thenReturn(10L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.save(place);

        assertEquals(10L, result.getServerVersion());
        assertEquals("user1", result.getLastModifiedBy());
        assertEquals("123 Main St", result.getAddress());
        verify(placeRepository).save(place);
        verify(placeSearchIndexSyncService).upsert(place);
    }

    @Test
    void saveFromSearch_WhenPlaceDoesNotExist_Persists() {
        Place place = new Place();
        place.setId("new1");
        when(placeAddressEnrichmentService.enrichAddress(any(), any(), any()))
            .thenReturn("456 Search Rd");
        when(placeRepository.findById("new1")).thenReturn(Optional.empty());
        when(syncVersionService.nextVersion()).thenReturn(5L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.saveFromSearch(place);

        assertEquals("osm_geocoder", result.getPlaceSource());
        assertEquals("search_discovered", result.getPersistedByAction());
        assertEquals("456 Search Rd", result.getAddress());
        assertEquals(5L, result.getServerVersion());
        verify(placeRepository).save(place);
        verify(placeSearchIndexSyncService).upsert(place);
    }

    @Test
    void saveFromSearch_WhenPlaceExists_ReturnsExisting() {
        Place existing = new Place();
        existing.setId("existing1");
        existing.setName("Already saved");
        Place input = new Place();
        input.setId("existing1");
        when(placeRepository.findById("existing1")).thenReturn(Optional.of(existing));

        Place result = placeService.saveFromSearch(input);

        assertSame(existing, result);
        verify(placeRepository, never()).save(any());
        verifyNoInteractions(placeSearchIndexSyncService);
    }

    @Test
    void deleteById_WhenExists_SoftDeletesAndReturnsTrue() {
        Place place = new Place();
        place.setId("p1");
        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));
        when(syncVersionService.nextVersion()).thenReturn(11L);
        when(placeRepository.save(place)).thenReturn(place);

        boolean result = placeService.deleteById("p1");

        assertTrue(result);
        assertTrue(place.isDeleted());
        assertEquals(11L, place.getServerVersion());
        verify(placeRepository).save(place);
        verify(placeSearchIndexSyncService).deleteById("p1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(placeRepository.findById("p1")).thenReturn(Optional.empty());

        boolean result = placeService.deleteById("p1");

        assertFalse(result);
        verify(placeRepository, never()).save(any());
        verifyNoInteractions(placeSearchIndexSyncService);
    }

    @Test
    void search_DelegatesToRepository() {
        Place place = new Place();
        when(placeSearchProvider.search("test"))
                .thenReturn(List.of(place));

        List<Place> result = placeService.search("test");

        assertEquals(1, result.size());
        verify(placeSearchProvider).search("test");
    }

    @Test
    void getByTag_DelegatesToRepository() {
        Place place = new Place();
        when(placeRepository.findByTagsContaining("church"))
                .thenReturn(List.of(place));

        List<Place> result = placeService.getByTag("church");

        assertEquals(1, result.size());
    }

    @Test
    void getByUserId_DelegatesToRepository() {
        Place place = new Place();
        when(placeRepository.findByPersistedByUserId("u1"))
                .thenReturn(List.of(place));

        List<Place> result = placeService.getByUserId("u1");

        assertEquals(1, result.size());
    }

    @Test
    void addReview_AppendsReviewAndRecalculatesRating() {
        Place place = new Place();
        place.setId("p1");
        place.setReviews(new ArrayList<>());
        PlaceReview existingReview = new PlaceReview();
        existingReview.setRating(4);
        place.getReviews().add(existingReview);

        PlaceReview newReview = new PlaceReview();
        newReview.setRating(2);
        newReview.setUserId("u2");

        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));
        when(syncVersionService.nextVersion()).thenReturn(12L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.addReview("p1", newReview);

        assertEquals(2, result.getReviewCount());
        assertEquals(3.0, result.getRating(), 0.01);
        assertNotNull(newReview.getCreatedAt());
        verify(placeRepository).save(place);
        verify(placeSearchIndexSyncService).upsert(place);
    }

    @Test
    void addReview_WhenPlaceNotFound_ThrowsException() {
        when(placeRepository.findById("missing")).thenReturn(Optional.empty());
        PlaceReview review = new PlaceReview();

        assertThrows(NoSuchElementException.class,
                () -> placeService.addReview("missing", review));
    }

    @Test
    void addReview_WhenReviewsNull_InitializesListAndAdds() {
        Place place = new Place();
        place.setId("p1");
        place.setReviews(null);

        PlaceReview review = new PlaceReview();
        review.setRating(5);

        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));
        when(syncVersionService.nextVersion()).thenReturn(1L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.addReview("p1", review);

        assertEquals(1, result.getReviewCount());
        assertEquals(5.0, result.getRating(), 0.01);
        verify(placeSearchIndexSyncService).upsert(place);
    }
}
