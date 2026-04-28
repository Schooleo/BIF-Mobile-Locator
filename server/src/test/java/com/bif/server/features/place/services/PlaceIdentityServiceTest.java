package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceIdentityServiceTest {

    @Mock
    private PlaceMappingRepository placeMappingRepository;

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private PlaceIdentityService placeIdentityService;

    @BeforeEach
    void setUp() {
        placeIdentityService = new PlaceIdentityService(placeMappingRepository, placeRepository, mongoTemplate);
    }

    @Test
    void resolveInternalPlaceId_WhenExactMatchExists_ReturnsExistingId() {
        PlaceMapping mapping = new PlaceMapping();
        mapping.setInternalPlaceId("existing-internal-id");
        when(placeMappingRepository.findByExternalSourceAndExternalId("OSM", "ext-123"))
                .thenReturn(Optional.of(mapping));

        String result = placeIdentityService.resolveInternalPlaceId("OSM", "ext-123", 10.0, 106.0, "Place Name");

        assertEquals("existing-internal-id", result);
        verify(placeMappingRepository, never()).save(any());
        verifyNoInteractions(placeRepository);
    }

    @Test
    void resolveInternalPlaceId_WhenNearbyMatchWithSimilarNameExists_ReturnsMatchedId() {
        // Arrange
        when(placeMappingRepository.findByExternalSourceAndExternalId(any(), any()))
                .thenReturn(Optional.empty());

        PlaceMapping nearby = new PlaceMapping();
        nearby.setInternalPlaceId("nearby-id");
        nearby.setName("Bui Vien Street");
        
        when(mongoTemplate.find(any(Query.class), eq(PlaceMapping.class)))
                .thenReturn(List.of(nearby));

        // Act
        String result = placeIdentityService.resolveInternalPlaceId("OSM", "ext-456", 10.769, 106.693, "Bùi Viện Street");

        // Assert
        assertEquals("nearby-id", result);
        // Should create a new mapping for the new source/id pointing to existing internalId
        verify(placeMappingRepository).save(any(PlaceMapping.class));
        verifyNoInteractions(placeRepository);
    }

    @Test
    void resolveInternalPlaceId_WhenNoMatch_GeneratesNewIdAndSaves() {
        // Arrange
        when(placeMappingRepository.findByExternalSourceAndExternalId(any(), any()))
                .thenReturn(Optional.empty());
        when(mongoTemplate.find(any(Query.class), eq(PlaceMapping.class)))
                .thenReturn(Collections.emptyList());

        // Act
        String result = placeIdentityService.resolveInternalPlaceId("NEW_SOURCE", "ext-999", 21.028, 105.834, "Hanoi Citadel");

        // Assert
        assertNotNull(result);
        assertDoesNotThrow(() -> java.util.UUID.fromString(result));
        
        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());
        assertEquals(result, placeCaptor.getValue().getId());
        assertEquals("Hanoi Citadel", placeCaptor.getValue().getName());

        ArgumentCaptor<PlaceMapping> mappingCaptor = ArgumentCaptor.forClass(PlaceMapping.class);
        verify(placeMappingRepository).save(mappingCaptor.capture());
        assertEquals(result, mappingCaptor.getValue().getInternalPlaceId());
        assertEquals("NEW_SOURCE", mappingCaptor.getValue().getExternalSource());
        assertEquals("ext-999", mappingCaptor.getValue().getExternalId());
    }
}
