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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

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
        Authentication auth = new UsernamePasswordAuthenticationToken("u1",
                null);
        SyncResponse response = new SyncResponse();
        when(syncService.sync(any(SyncRequest.class))).thenReturn(response);

        SyncResponse result = controller.syncPreview(null, auth);

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncService).sync(captor.capture());
        assertEquals("u1", captor.getValue().getUserId());
        assertEquals(0, captor.getValue().getLastPulledVersion());
        assertSame(response, result);
    }

    @Test
    void syncPreview_WithVersion_UsesProvidedBaseline() {
        Authentication auth = new UsernamePasswordAuthenticationToken("u1",
                null);
        SyncResponse response = new SyncResponse();
        when(syncService.sync(any(SyncRequest.class))).thenReturn(response);

        controller.syncPreview(15, auth);

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncService).sync(captor.capture());
        assertEquals("u1", captor.getValue().getUserId());
        assertEquals(15, captor.getValue().getLastPulledVersion());
    }

    @Test
    void sync_DelegatesToServiceAndOverridesUserId() {
        SyncRequest input = new SyncRequest();
        input.setUserId("spoofed");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "u1", null);
        SyncResponse response = new SyncResponse();
        when(syncService.sync(input)).thenReturn(response);

        SyncResponse result = controller.sync(input, auth);

        assertSame(response, result);
        assertEquals("u1", input.getUserId());
        verify(syncService).sync(input);
    }

    @Test
    void syncPreview_WhenAuthenticationMissing_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.syncPreview(0, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(syncService, never()).sync(any());
    }

    @Test
    void sync_WhenAuthenticationMissing_ThrowsUnauthorized() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.sync(new SyncRequest(), null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(syncService, never()).sync(any());
    }
}
