package com.bif.server.features.place.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceAddressEnrichmentService;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.PlaceSearchProvider;
import com.bif.server.features.sync.services.SyncVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    private PlaceIdentityService placeIdentityService;

    @Mock
    private PlaceMappingRepository placeMappingRepository;

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
            placeIdentityService,
            placeMappingRepository,
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
        place.setLocation(new Location(10.7758, 106.7010));
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
    void save_WhenIdentityMetadataPresent_ResolvesCanonicalIdAndUpsertsMapping() {
        Place place = new Place();
        place.setId("alias-1");
        place.setName("Cho Ben Thanh");
        place.setPlaceSource("GOOGLE_MAPS");
        place.setPersistedByUserId("user1");
        place.setLocation(new Location(10.7720, 106.6980));

        when(placeAddressEnrichmentService.enrichAddress(any(), any(), any()))
                .thenReturn("456 Search Rd");
        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "alias-1", 10.7720, 106.6980, "Cho Ben Thanh"))
                .thenReturn("canonical-1");
        when(syncVersionService.nextVersion()).thenReturn(11L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.save(place);

        assertEquals("canonical-1", result.getId());
        verify(placeRepository).save(place);
        verify(placeMappingRepository).upsertByExternalKey("GOOGLE_MAPS", "alias-1", "canonical-1", "Cho Ben Thanh", 10.7720, 106.6980);
        verify(placeSearchIndexSyncService).upsert(place);
    }

    @Test
    void save_WhenCanonicalResolvesExisting_MergesMissingFieldsWithoutDataLoss() {
        Place existing = new Place();
        existing.setId("canonical-1");
        existing.setName("Cho Ben Thanh");
        existing.setAddress("Old Address");
        existing.setPersistedByUserId("u-existing");
        existing.setTags(List.of("market"));
        existing.setRating(4.2);
        existing.setReviewCount(15);

        Place input = new Place();
        input.setId("alias-1");
        input.setName("Cho Ben Thanh");
        input.setPlaceSource("GOOGLE_MAPS");
        input.setLocation(new Location(10.7720, 106.6980));

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "alias-1", 10.7720, 106.6980, "Cho Ben Thanh"))
                .thenReturn("canonical-1");
        when(placeRepository.findById("canonical-1")).thenReturn(Optional.of(existing));
        when(placeAddressEnrichmentService.enrichAddress(any(), any(), any())).thenReturn("Old Address");
        when(syncVersionService.nextVersion()).thenReturn(12L);
        when(placeRepository.save(input)).thenReturn(input);

        Place result = placeService.save(input);

        assertEquals("canonical-1", result.getId());
        assertEquals("Old Address", result.getAddress());
        assertEquals("u-existing", result.getPersistedByUserId());
        assertEquals("u-existing", result.getLastModifiedBy());
        assertEquals(List.of("market"), result.getTags());
        assertEquals(4.2, result.getRating());
        assertEquals(15, result.getReviewCount());
        verify(placeMappingRepository).upsertByExternalKey("GOOGLE_MAPS", "alias-1", "canonical-1", "Cho Ben Thanh", 10.7720, 106.6980);
        verify(placeSearchIndexSyncService).upsert(input);
    }

    @Test
    void saveFromSearch_WhenPlaceDoesNotExist_Persists() {
        Place place = new Place();
        place.setId("new1");
        place.setName("Cho Ben Thanh");
        place.setPlaceSource("osm_geocoder");
        place.setLocation(new Location(10.7720, 106.6980));
        when(placeAddressEnrichmentService.enrichAddress(any(), any(), any()))
            .thenReturn("456 Search Rd");
        when(placeIdentityService.resolveInternalPlaceId("osm_geocoder", "new1", 10.7720, 106.6980, "Cho Ben Thanh"))
                .thenReturn("canonical-1");
        when(placeRepository.findById("canonical-1")).thenReturn(Optional.empty());
        when(syncVersionService.nextVersion()).thenReturn(5L);
        when(placeRepository.save(place)).thenReturn(place);

        Place result = placeService.saveFromSearch(place);

        assertEquals("osm_geocoder", result.getPlaceSource());
        assertEquals("canonical-1", result.getId());
        assertEquals("search_discovered", result.getPersistedByAction());
        assertEquals("456 Search Rd", result.getAddress());
        assertEquals(5L, result.getServerVersion());
        verify(placeRepository).save(place);
        verify(placeMappingRepository).upsertByExternalKey("osm_geocoder", "new1", "canonical-1", "Cho Ben Thanh", 10.7720, 106.6980);
        verify(placeSearchIndexSyncService).upsert(place);
    }

    @Test
    void saveFromSearch_WhenPlaceExists_ReturnsExisting() {
        Place existing = new Place();
        existing.setId("existing1");
        existing.setName("Already saved");
        existing.setLocation(new Location(10.78, 106.69));
        Place input = new Place();
        input.setId("existing1");
        input.setName("Already saved");
        input.setPlaceSource("osm_geocoder");
        input.setLocation(new Location(10.78, 106.69));
        when(placeIdentityService.resolveInternalPlaceId("osm_geocoder", "existing1", 10.78, 106.69, "Already saved"))
                .thenReturn("existing1");
        when(placeRepository.findById("existing1")).thenReturn(Optional.of(existing));

        Place result = placeService.saveFromSearch(input);

        assertSame(existing, result);
        verify(placeRepository, never()).save(any());
        verify(placeMappingRepository).upsertByExternalKey("osm_geocoder", "existing1", "existing1", "Already saved", 10.78, 106.69);
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
    void saveFromSearch_WhenGeocodeDuplicateNearby_ReturnsExistingWithoutSaving() {
        Place existing = new Place();
        existing.setId("mongo_1");
        existing.setName("Ben Thanh Market");
        existing.setLocation(new Location(10.77200, 106.69800));

        Place input = new Place();
        input.setId("geocode_10.7722_106.6982");
        input.setName("ben thanh market");
        input.setPlaceSource("osm_geocoder");
        input.setLocation(new Location(10.77220, 106.69820));

        when(placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                "ben thanh market", "ben thanh market"))
                .thenReturn(List.of(existing));

        Place result = placeService.saveFromSearch(input);

        assertSame(existing, result);
        verify(placeRepository, never()).save(any());
        verify(placeMappingRepository).upsertByExternalKey("osm_geocoder", "geocode_10.7722_106.6982", "mongo_1", "Ben Thanh Market", 10.77200, 106.69800);
        verifyNoInteractions(placeSearchIndexSyncService);
    }

    @Test
    void save_WhenCoordinatesInvalid_ThrowsAndSkipsPersistence() {
        Place place = new Place();
        place.setId("p_bad");
        place.setLocation(new Location(0.0, 0.0));

        assertThrows(IllegalArgumentException.class, () -> placeService.save(place));
        verify(placeRepository, never()).save(any());
        verifyNoInteractions(placeSearchIndexSyncService);
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
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery("test");
        when(placeSearchProvider.search(request))
                .thenReturn(List.of(place));

        List<Place> result = placeService.search(request);

        assertEquals(1, result.size());
        verify(placeSearchProvider).search(request);
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
}
