package com.bif.server.features.sync.services;

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

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService(syncVersionService, syncChangeRepository);
    }

    @Test
    void sync_WhenNoPushChanges_ReturnsPulledChanges() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(5);
        request.setPushedChanges(null);

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType("place");
        entry.setEntityId("p1");
        entry.setServerVersion(6);
        entry.setOperation("CREATE");
        entry.setTimestamp(Instant.now());

        when(syncChangeRepository
                .findByServerVersionGreaterThanOrderByServerVersionAsc(5))
                .thenReturn(List.of(entry));
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);

        SyncResponse response = syncService.sync(request);

        assertEquals(6, response.getCurrentServerVersion());
        assertEquals(1, response.getPulledChanges().size());
        assertEquals("place", response.getPulledChanges().get(0).getEntityType());
        assertNull(response.getConflicts());
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
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("client-1"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByServerVersionGreaterThanOrderByServerVersionAsc(5))
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
                .findByServerVersionGreaterThanOrderByServerVersionAsc(5))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(syncChangeRepository, never()).save(any());
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
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByServerVersionGreaterThanOrderByServerVersionAsc(5))
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
    void sync_WhenBaselineNegative_UsesZero() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(-2);
        request.setPushedChanges(null);

        when(syncChangeRepository
                .findByServerVersionGreaterThanOrderByServerVersionAsc(0))
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
                .findByServerVersionGreaterThanOrderByServerVersionAsc(0))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(syncChangeRepository, never()).findByClientChangeId(any());
        verify(syncChangeRepository).save(any());
    }
}
