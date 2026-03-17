package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.services.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    @Mock
    private SyncService syncService;

    private SyncController controller;

    @BeforeEach
    void setUp() {
        controller = new SyncController(syncService);
    }

    @Test
    void sync_DelegatesToService() {
        SyncRequest request = new SyncRequest();
        SyncResponse response = new SyncResponse();
        when(syncService.sync(request)).thenReturn(response);

        SyncResponse result = controller.sync(request);

        assertSame(response, result);
        verify(syncService).sync(request);
    }
}
