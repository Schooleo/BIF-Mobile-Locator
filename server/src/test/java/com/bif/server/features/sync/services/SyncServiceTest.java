package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SyncServiceTest {

    private SyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new SyncService();
    }

    @Test
    void sync_WhenNoPushChanges_ReturnsBaselineVersion() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(5);
        request.setPushedChanges(null);

        SyncResponse response = syncService.sync(request);

        assertEquals(5, response.getCurrentServerVersion());
        assertNotNull(response.getPulledChanges());
        assertTrue(response.getPulledChanges().isEmpty());
    }

    @Test
    void sync_WhenPushChangesExist_IncrementsVersionByCount() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(7);
        request.setPushedChanges(List.of(new SyncChange(), new SyncChange(), new SyncChange()));

        SyncResponse response = syncService.sync(request);

        assertEquals(10, response.getCurrentServerVersion());
    }

    @Test
    void sync_WhenBaselineNegative_UsesZero() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(-2);
        request.setPushedChanges(List.of(new SyncChange()));

        SyncResponse response = syncService.sync(request);

        assertEquals(1, response.getCurrentServerVersion());
    }
}
