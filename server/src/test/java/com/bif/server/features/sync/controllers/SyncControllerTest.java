package com.bif.server.features.sync.controllers;

import com.bif.server.features.sync.models.SyncRequest;
import com.bif.server.features.sync.models.SyncPushResult;
import com.bif.server.features.sync.models.SyncResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bif.server.features.sync.services.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

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
    void sync_DelegatesToServiceAndOverridesUserId() {
        // Arrange
        SyncRequest request = new SyncRequest();
        request.setUserId("spoofed-user");
        Authentication auth = new UsernamePasswordAuthenticationToken("user-1", null);
        SyncResponse response = new SyncResponse();
        when(syncService.sync(request)).thenReturn(response);

        // Act
        SyncResponse result = controller.sync(request, auth);

        // Assert
        assertSame(response, result);
        assertEquals("user-1", request.getUserId());
        verify(syncService).sync(request);
    }

    @Test
    void sync_WhenAuthenticationMissing_ThrowsUnauthorized() {
        // Arrange
        SyncRequest request = new SyncRequest();

        // Act & Assert
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.sync(request, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(syncService, never()).sync(any());
    }

    @Test
    void syncResponse_serializesPushResults() throws Exception {
        SyncResponse response = new SyncResponse();
        response.setPushResults(java.util.List.of(
                new SyncPushResult("cid-1", "APPLIED", "APPLIED")
        ));

        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"pushResults\""));
        assertTrue(json.contains("\"status\":\"APPLIED\""));
        assertTrue(json.contains("\"reasonCode\":\"APPLIED\""));
    }
}
