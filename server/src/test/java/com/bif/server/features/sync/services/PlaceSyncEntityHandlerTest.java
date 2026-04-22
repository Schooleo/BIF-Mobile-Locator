package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceAddressEnrichmentService;
import com.bif.server.features.place.services.PlaceIdentityService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceSyncEntityHandlerTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private PlaceMappingRepository placeMappingRepository;

    @Mock
    private PlaceAddressEnrichmentService placeAddressEnrichmentService;

    @Mock
    private PlaceIdentityService placeIdentityService;

    private PlaceSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PlaceSyncEntityHandler(placeRepository,
                placeMappingRepository,
                placeAddressEnrichmentService,
                placeIdentityService);
    }

    @Test
    void applyPushedChange_whenAddressMissing_enrichesBeforeSave() {
        when(placeRepository.findById("p1")).thenReturn(Optional.empty());
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
        verifyNoInteractions(placeIdentityService);
        verify(placeMappingRepository, never()).upsertByExternalKey(anyString(), anyString(), anyString(), anyString(), anyDouble(), anyDouble());
    }

    @Test
    void resolvePayload_whenEntryHasPayload_returnsIt() {
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setPayload("{\"id\":\"p1\"}");

        String payload = handler.resolvePayload(entry);

        assertEquals("{\"id\":\"p1\"}", payload);
    }

    @Test
    void applyPushedChange_whenCanonicalResolved_savesCanonicalAndReturnsTombstoneForAlias() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("ext-123");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"ext-123\",\"name\":\"Cafe\",\"placeSource\":\"GOOGLE_MAPS\",\"externalId\":\"ext-123\",\"latitude\":10.0,\"longitude\":20.0}");

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "ext-123", 10.0, 20.0, "Cafe"))
                .thenReturn("canonical-1");
        when(placeRepository.findById("canonical-1")).thenReturn(Optional.empty());
        when(placeAddressEnrichmentService.enrichAddress(any(), eq(10.0), eq(20.0))).thenReturn("Server Address");

        String resultPayload = handler.applyPushedChange(pushed, "user-1", 21L);

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());
        Place saved = placeCaptor.getValue();
        assertEquals("canonical-1", saved.getId());
        assertEquals("GOOGLE_MAPS", saved.getPlaceSource());
        verify(placeMappingRepository).upsertByExternalKey("GOOGLE_MAPS", "ext-123", "canonical-1", "Cafe", 10.0, 20.0);
        assertTrue(resultPayload.contains("\"id\":\"ext-123\""));
        assertTrue(resultPayload.contains("\"deleted\":true"));
        assertTrue(resultPayload.contains("\"serverVersion\":21"));
    }
}
