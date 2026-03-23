package com.bif.server.features.sync.services;

import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.sync.models.*;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private SyncVersionService syncVersionService;

    @Mock
    private SyncChangeRepository syncChangeRepository;

        @Mock
        private PlaceRepository placeRepository;

        @Mock
        private SyncEntityHandler placeSyncEntityHandler;

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        when(placeSyncEntityHandler.entityType()).thenReturn("place");
                syncService = new SyncService(syncVersionService,
                                syncChangeRepository,
                List.of(placeSyncEntityHandler));
    }

    @Test
    void sync_WhenNoPushChanges_ReturnsPulledChanges() {
        SyncRequest request = new SyncRequest();
                request.setUserId("user1");
        request.setLastPulledVersion(5);
        request.setPushedChanges(null);

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType("place");
        entry.setEntityId("p1");
        entry.setServerVersion(6);
        entry.setOperation("CREATE");
        entry.setPayload("{\"id\":\"p1\",\"name\":\"Payload Place\"}");
        entry.setTimestamp(Instant.now());

        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(List.of(entry));
        when(placeSyncEntityHandler.resolvePayload(entry))
                .thenReturn(entry.getPayload());
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);

        SyncResponse response = syncService.sync(request);

        assertEquals(6, response.getCurrentServerVersion());
        assertEquals(1, response.getPulledChanges().size());
        assertEquals("place", response.getPulledChanges().get(0).getEntityType());
        assertEquals("{\"id\":\"p1\",\"name\":\"Payload Place\"}",
                response.getPulledChanges().get(0).getPayload());
        assertNull(response.getConflicts());
    }

    @Test
    void sync_WhenPullContainsJustPushedClientChangeId_filtersEchoedEntry() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("client-echo");
        request.setPushedChanges(List.of(pushed));

        SyncChangeEntry echoedEntry = new SyncChangeEntry();
        echoedEntry.setEntityType("place");
        echoedEntry.setEntityId("p1");
        echoedEntry.setServerVersion(6);
        echoedEntry.setOperation("UPDATE");
        echoedEntry.setClientChangeId("client-echo");
        echoedEntry.setPayload("{\"id\":\"p1\"}");

        when(syncChangeRepository.findByClientChangeId("client-echo"))
                .thenReturn(Optional.empty());
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn("{\"id\":\"p1\"}");
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(List.of(echoedEntry));

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        assertTrue(response.getPulledChanges().isEmpty());
    }

    @Test
    void sync_WhenPushChanges_PersistsToChangeLog() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setServerVersion(5);
        pushed.setOperation("CREATE");
        pushed.setClientChangeId("client-1");
        pushed.setPayload("{\"id\":\"p1\",\"name\":\"Cafe\","
                + "\"address\":\"A\",\"rating\":4.2,"
                + "\"latitude\":1.0,\"longitude\":2.0}");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("client-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p1"))
                .thenReturn(Optional.empty());
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn("{\"id\":\"p1\"}");
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);
        assertNotNull(response);

        ArgumentCaptor<SyncChangeEntry> captor =
                ArgumentCaptor.forClass(SyncChangeEntry.class);
        verify(syncChangeRepository).save(captor.capture());
        SyncChangeEntry saved = captor.getValue();
        assertEquals("place", saved.getEntityType());
        assertEquals("p1", saved.getEntityId());
        assertEquals(6L, saved.getServerVersion());
        assertEquals("client-1", saved.getClientChangeId());
        assertEquals("user1", saved.getUserId());
                assertNotNull(saved.getPayload());
        verify(placeSyncEntityHandler)
                .applyPushedChange(any(), any(), anyLong());
    }

    @Test
    void sync_WhenDuplicateClientChangeId_SkipsChange() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setClientChangeId("already-processed");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("already-processed"))
                .thenReturn(Optional.of(new SyncChangeEntry()));
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        null, 5))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(syncChangeRepository, never()).save(any());
        verify(placeSyncEntityHandler, never())
                .applyPushedChange(any(), any(), anyLong());
    }

    @Test
    void sync_WhenVersionConflict_ReportsConflictButAccepts() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setServerVersion(3); // Client has version 3, server is at 5
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("conflict-1");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("conflict-1"))
                .thenReturn(Optional.empty());
        SyncChangeEntry latestEntityVersion = new SyncChangeEntry();
        latestEntityVersion.setServerVersion(5);
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p1"))
                .thenReturn(Optional.of(latestEntityVersion));
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn(pushed.getPayload());
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        // Change still saved (LWW)
        verify(syncChangeRepository).save(any());
        // But conflict reported
        assertNotNull(response.getConflicts());
        assertEquals(1, response.getConflicts().size());
        SyncConflict conflict = response.getConflicts().get(0);
        assertEquals("place", conflict.getEntityType());
        assertEquals("p1", conflict.getEntityId());
        assertEquals(3, conflict.getClientVersion());
        assertEquals(5, conflict.getServerVersion());
        assertEquals("SERVER_WINS", conflict.getResolution());
    }

    @Test
    void sync_WhenGlobalVersionAheadButSameEntityNotAhead_DoesNotConflict() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(10);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("favorite");
        pushed.setEntityId("fav-1");
        pushed.setServerVersion(10);
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("fav-change");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("fav-change"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "favorite", "fav-1"))
                .thenReturn(Optional.of(new SyncChangeEntry()));
        when(syncVersionService.nextVersion()).thenReturn(12L);
        when(syncVersionService.getCurrentVersion()).thenReturn(12L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 10))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        assertNull(response.getConflicts());
    }

    @Test
    void sync_WhenBaselineNegative_UsesZero() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(-2);
        request.setPushedChanges(null);

        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 0))
                .thenReturn(Collections.emptyList());
        when(syncVersionService.getCurrentVersion()).thenReturn(0L);

        SyncResponse response = syncService.sync(request);

        assertEquals(0, response.getCurrentServerVersion());
        assertTrue(response.getPulledChanges().isEmpty());
    }

    @Test
    void sync_WhenNullClientChangeId_SkipsIdempotencyCheck() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(0);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("favorite");
        pushed.setEntityId("f1");
        pushed.setServerVersion(0);
        pushed.setOperation("CREATE");
        pushed.setClientChangeId(null);
        request.setPushedChanges(List.of(pushed));

        when(syncVersionService.getCurrentVersion()).thenReturn(0L);
        when(syncVersionService.nextVersion()).thenReturn(1L);
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "favorite", "f1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 0))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(syncChangeRepository, never()).findByClientChangeId(any());
        verify(syncChangeRepository).save(any());
    }

    @Test
        void sync_WhenDeletePlacePush_UsesPlaceHandlerAndStoresPayload() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(2);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p-del");
        pushed.setOperation("DELETE");
        pushed.setClientChangeId("delete-1");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("delete-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p-del"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(2L);
        when(syncVersionService.nextVersion()).thenReturn(3L);
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn("{\"id\":\"p-del\",\"deleted\":true}");
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 2))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(placeSyncEntityHandler)
                .applyPushedChange(any(), any(), anyLong());

        ArgumentCaptor<SyncChangeEntry> changeCaptor =
                ArgumentCaptor.forClass(SyncChangeEntry.class);
        verify(syncChangeRepository).save(changeCaptor.capture());
        assertNotNull(changeCaptor.getValue().getPayload());
        assertTrue(changeCaptor.getValue().getPayload().contains("\"deleted\":true"));
    }
}
