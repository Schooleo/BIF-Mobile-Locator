package com.bif.server.features.place.services;

import com.bif.server.features.place.models.PlaceIdentityLock;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceIdentityServiceTest {

    @Mock
    private PlaceMappingRepository placeMappingRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PlaceCleanupService placeCleanupService;

    private PlaceIdentityService placeIdentityService;

    @BeforeEach
    void setUp() {
    placeIdentityService = new PlaceIdentityService(
        placeMappingRepository,
        mongoTemplate,
        placeCleanupService);

    PlaceIdentityLock acquired = new PlaceIdentityLock();
    acquired.setId("lock-id");
    when(mongoTemplate.findAndModify(
        any(Query.class),
        any(Update.class),
        any(FindAndModifyOptions.class),
        eq(PlaceIdentityLock.class)))
        .thenReturn(acquired);
    }

    @Test
    void resolveInternalPlaceId_WhenExactMatchExists_ReturnsExistingId() {
        PlaceMapping mapping = new PlaceMapping();
        mapping.setInternalPlaceId("existing-internal-id");
        when(placeMappingRepository.findByExternalSourceAndExternalId("OSM", "ext-123"))
                .thenReturn(Optional.of(mapping));
    when(placeCleanupService.reviveOrphanedPlace("existing-internal-id")).thenReturn(1L);

        String result = placeIdentityService.resolveInternalPlaceId("OSM", "ext-123", 10.0, 106.0, "Place Name");

        assertEquals("existing-internal-id", result);
    verify(mongoTemplate, never()).find(any(Query.class), eq(PlaceMapping.class));
    verify(mongoTemplate, never()).upsert(any(Query.class), any(Update.class), eq(com.bif.server.features.place.models.Place.class));
    }

    @Test
    void resolveInternalPlaceId_WhenNearbyMatchWithSimilarNameExists_ReturnsMatchedId() {
        when(placeMappingRepository.findByExternalSourceAndExternalId(any(), any()))
                .thenReturn(Optional.empty());

        PlaceMapping nearby = new PlaceMapping();
        nearby.setInternalPlaceId("nearby-id");
        nearby.setName("Bui Vien Street");

        when(mongoTemplate.find(any(Query.class), eq(PlaceMapping.class)))
                .thenReturn(List.of(nearby));
    when(placeCleanupService.reviveOrphanedPlace("nearby-id")).thenReturn(1L);

    PlaceMapping resolvedMapping = new PlaceMapping();
    resolvedMapping.setInternalPlaceId("nearby-id");
    when(placeMappingRepository.upsertByExternalKey("OSM", "ext-456", "nearby-id", "Bùi Viện Street", 10.769, 106.693))
            .thenReturn(resolvedMapping);

        String result = placeIdentityService.resolveInternalPlaceId("OSM", "ext-456", 10.769, 106.693, "Bùi Viện Street");

        assertEquals("nearby-id", result);
    verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(com.bif.server.features.place.models.Place.class));
    }

    @Test
    void resolveInternalPlaceId_WhenNoMatch_UsesUpsertedInternalId() {
        when(placeMappingRepository.findByExternalSourceAndExternalId(any(), any()))
                .thenReturn(Optional.empty());
        when(mongoTemplate.find(any(Query.class), eq(PlaceMapping.class)))
                .thenReturn(Collections.emptyList());

    PlaceMapping upsertedMapping = new PlaceMapping();
    upsertedMapping.setInternalPlaceId("resolved-new-id");
    when(placeMappingRepository.upsertByExternalKey(eq("NEW_SOURCE"), eq("ext-999"), anyString(), eq("Hanoi Citadel"), eq(21.028), eq(105.834)))
            .thenReturn(upsertedMapping);

        String result = placeIdentityService.resolveInternalPlaceId("NEW_SOURCE", "ext-999", 21.028, 105.834, "Hanoi Citadel");

    assertEquals("resolved-new-id", result);
    verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(com.bif.server.features.place.models.Place.class));
    }

    @Test
    void resolveInternalPlaceId_WhenMappingUpsertConflicts_ReusesConcurrentMapping() {
    PlaceMapping concurrentMapping = new PlaceMapping();
    concurrentMapping.setInternalPlaceId("concurrent-id");

    when(placeMappingRepository.findByExternalSourceAndExternalId("OSM", "ext-dup"))
        .thenReturn(Optional.empty(), Optional.of(concurrentMapping));
    when(mongoTemplate.find(any(Query.class), eq(PlaceMapping.class)))
        .thenReturn(Collections.emptyList());

    when(placeMappingRepository.upsertByExternalKey(eq("OSM"), eq("ext-dup"), anyString(), eq("Concurrent Place"), eq(10.0), eq(106.0)))
            .thenReturn(null);

    String result = placeIdentityService.resolveInternalPlaceId("OSM", "ext-dup", 10.0, 106.0, "Concurrent Place");

    assertEquals("concurrent-id", result);
    verify(mongoTemplate).upsert(any(Query.class), any(Update.class), eq(com.bif.server.features.place.models.Place.class));
    }
}
