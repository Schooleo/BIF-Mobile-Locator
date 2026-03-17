package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncResponse;
import com.bif.server.features.sync.services.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncGraphqlControllerTest {

    @Mock
    private SyncService syncService;

    private SyncGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new SyncGraphqlController(syncService);
    }

    @Test
    void syncPreview_WithNullVersion_UsesZeroBaseline() {
        SyncResponse response = new SyncResponse();
        when(syncService.sync(any(SyncRequest.class))).thenReturn(response);

        SyncResponse result = controller.syncPreview(null);

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncService).sync(captor.capture());
        assertEquals(0, captor.getValue().getLastPulledVersion());
        assertSame(response, result);
    }

    @Test
    void syncPreview_WithVersion_UsesProvidedBaseline() {
        SyncResponse response = new SyncResponse();
        when(syncService.sync(any(SyncRequest.class))).thenReturn(response);

        controller.syncPreview(15);

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncService).sync(captor.capture());
        assertEquals(15, captor.getValue().getLastPulledVersion());
    }

    @Test
    void sync_DelegatesToService() {
        SyncRequest input = new SyncRequest();
        SyncResponse response = new SyncResponse();
        when(syncService.sync(input)).thenReturn(response);

        SyncResponse result = controller.sync(input);

        assertSame(response, result);
        verify(syncService).sync(input);
    }
}
