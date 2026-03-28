package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceAddressEnrichmentService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSyncEntityHandlerTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceAddressEnrichmentService placeAddressEnrichmentService;

    private PlaceSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlaceSyncEntityHandler(placeRepository,
                placeAddressEnrichmentService);
    }

    @Test
    void applyPushedChange_whenAddressMissing_enrichesBeforeSave() {
        when(placeRepository.findById("p1")).thenReturn(Optional.empty());
        when(placeRepository.findByNameAndLocationLatitudeAndLocationLongitude(
                any(), any(double.class), any(double.class)))
                .thenReturn(java.util.Collections.emptyList());
        when(placeAddressEnrichmentService.enrichAddress(eq("Address unavailable"),
                eq(10.0), eq(20.0)))
                .thenReturn("123 Server Street");

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"p1\",\"name\":\"Cafe\","
                + "\"address\":\"Address unavailable\","
                + "\"latitude\":10.0,\"longitude\":20.0}");

        handler.applyPushedChange(pushed, "user-1", 7L);

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());
        Place saved = placeCaptor.getValue();
        assertEquals("123 Server Street", saved.getAddress());
        assertEquals(new Location(10.0, 20.0), saved.getLocation());
    }

    @Test
    void resolvePayload_whenEntryHasPayload_returnsIt() {
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setPayload("{\"id\":\"p1\"}");

        String payload = handler.resolvePayload(entry);

        assertEquals("{\"id\":\"p1\"}", payload);
    }
}
