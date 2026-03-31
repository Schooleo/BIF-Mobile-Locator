package com.bif.app.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.PlaceDto;
import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.core.network.dto.SyncRequestDto;
import com.bif.app.core.network.dto.SyncResponseDto;
import com.bif.app.data.source.local.SyncQueueDao;
import com.bif.app.data.source.local.entity.SyncQueueEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class SyncManagerTest {

    @Mock
    private RestApiService mockRestApiService;
    @Mock
    private SyncQueueDao mockSyncQueueDao;
    @Mock
    private NetworkMonitor mockNetworkMonitor;

    private SyncManager syncManager;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        syncManager = new SyncManager(
                mockRestApiService, mockSyncQueueDao, mockNetworkMonitor);
        syncManager.setUserContext("user1", "device1");
        syncManager.setLastPulledVersion(0);
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void sync_whenOffline_returnsNullAndDoesNothing() {
        when(mockNetworkMonitor.isOnline()).thenReturn(false);

        SyncResponseDto result = syncManager.sync();

        assertNull(result);
        verify(mockRestApiService, never()).sync(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_whenOnlineNoPending_sendsEmptyPushAndPulls()
            throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockSyncQueueDao.getPending())
                .thenReturn(new ArrayList<>());

        SyncResponseDto serverResponse = new SyncResponseDto();
        serverResponse.currentServerVersion = 5;
        serverResponse.pulledChanges = new ArrayList<>();

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(serverResponse));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        SyncResponseDto result = syncManager.sync();

        assertNotNull(result);
        assertEquals(5, result.currentServerVersion);
        assertEquals(5, syncManager.getLastPulledVersion());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_whenOnlineWithPending_pushesThenRemoves()
            throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        SyncQueueEntity entry = new SyncQueueEntity();
        entry.id = 42;
        entry.entityType = "place";
        entry.entityId = "p1";
        entry.operation = "CREATE";
        entry.clientChangeId = "client-uuid-1";
        entry.status = "PENDING";
        when(mockSyncQueueDao.getPending())
                .thenReturn(Collections.singletonList(entry));

        SyncResponseDto serverResponse = new SyncResponseDto();
        serverResponse.currentServerVersion = 10;
        serverResponse.pulledChanges = new ArrayList<>();

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(serverResponse));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        SyncResponseDto result = syncManager.sync();

        assertNotNull(result);
        // Verify the entry was marked IN_FLIGHT then removed
        verify(mockSyncQueueDao).update(entry);
        assertEquals("IN_FLIGHT", entry.status);
        verify(mockSyncQueueDao).remove(42);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_whenServerFails_incrementsRetryCount()
            throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        SyncQueueEntity entry = new SyncQueueEntity();
        entry.id = 1;
        entry.retryCount = 0;
        entry.status = "PENDING";
        when(mockSyncQueueDao.getPending())
                .thenReturn(Collections.singletonList(entry));

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.error(500,
                        okhttp3.ResponseBody.create(null, "")));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        SyncResponseDto result = syncManager.sync();

        assertNull(result);
        assertEquals(1, entry.retryCount);
        assertEquals("PENDING", entry.status);
        verify(mockSyncQueueDao, never()).remove(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_whenMaxRetriesExceeded_marksAsFailed()
            throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        SyncQueueEntity entry = new SyncQueueEntity();
        entry.id = 1;
        entry.retryCount = 5;
        entry.status = "PENDING";
        when(mockSyncQueueDao.getPending())
                .thenReturn(Collections.singletonList(entry));

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute()).thenThrow(new IOException("timeout"));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        SyncResponseDto result = syncManager.sync();

        assertNull(result);
        assertEquals(6, entry.retryCount);
        assertEquals("FAILED", entry.status);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_resetsInFlightOnStart() throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockSyncQueueDao.getPending())
                .thenReturn(new ArrayList<>());

        SyncResponseDto serverResponse = new SyncResponseDto();
        serverResponse.currentServerVersion = 0;
        serverResponse.pulledChanges = new ArrayList<>();

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(serverResponse));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        syncManager.sync();

        verify(mockSyncQueueDao).resetInFlight();
    }

    @Test
    public void enqueueChange_createsEntryInDao() {
        syncManager.enqueueChange("place", "p1", "CREATE", "uuid-1");

        ArgumentCaptor<SyncQueueEntity> captor =
                ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao, timeout(1000)).enqueue(captor.capture());

        SyncQueueEntity saved = captor.getValue();
        assertEquals("place", saved.entityType);
        assertEquals("p1", saved.entityId);
        assertEquals("CREATE", saved.operation);
        assertEquals("uuid-1", saved.clientChangeId);
        assertEquals("PENDING", saved.status);
        assertEquals(0, saved.retryCount);
    }

    @Test
    public void enqueueChange_withPayload_serializesPayload() {
        PlaceDto payload = new PlaceDto();
        payload.id = "p1";
        payload.name = "Cafe";

        syncManager.enqueueChange("place", "p1", "UPDATE",
                "uuid-2", payload);

        ArgumentCaptor<SyncQueueEntity> captor =
                ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao, timeout(1000)).enqueue(captor.capture());

        SyncQueueEntity saved = captor.getValue();
        assertNotNull(saved.payload);
        org.junit.Assert.assertTrue(saved.payload.contains("\"id\":\"p1\""));
        org.junit.Assert.assertTrue(saved.payload.contains("\"name\":\"Cafe\""));
    }

        @Test
        public void setUserContext_whenUserChanges_resetsLastPulledVersion() {
                syncManager.setLastPulledVersion(12);

                syncManager.setUserContext("user2", "device2");

                assertEquals(0, syncManager.getLastPulledVersion());
        }

        @Test
        public void setUserContext_whenUserUnchanged_keepsLastPulledVersion() {
                syncManager.setLastPulledVersion(12);

                syncManager.setUserContext("user1", "device1");

                assertEquals(12, syncManager.getLastPulledVersion());
        }

    @Test
    @SuppressWarnings("unchecked")
    public void sync_whenPendingHasPayload_includesPayloadInRequest()
            throws IOException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
                syncManager.setLastPulledVersion(7);

        SyncQueueEntity entry = new SyncQueueEntity();
        entry.id = 10;
        entry.entityType = "place";
        entry.entityId = "p10";
        entry.operation = "UPDATE";
        entry.clientChangeId = "cid-10";
        entry.payload = "{\"id\":\"p10\",\"name\":\"Updated\"}";
        entry.status = "PENDING";
        when(mockSyncQueueDao.getPending())
                .thenReturn(Collections.singletonList(entry));

        SyncResponseDto serverResponse = new SyncResponseDto();
        serverResponse.currentServerVersion = 11;
        serverResponse.pulledChanges = new ArrayList<>();

        Call<SyncResponseDto> mockCall =
                (Call<SyncResponseDto>) org.mockito.Mockito.mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(serverResponse));
        when(mockRestApiService.sync(any(SyncRequestDto.class)))
                .thenReturn(mockCall);

        syncManager.sync();

        ArgumentCaptor<SyncRequestDto> requestCaptor =
                ArgumentCaptor.forClass(SyncRequestDto.class);
        verify(mockRestApiService).sync(requestCaptor.capture());

        SyncRequestDto sent = requestCaptor.getValue();
        assertNotNull(sent.pushedChanges);
        assertEquals(1, sent.pushedChanges.size());
        assertEquals(entry.payload, sent.pushedChanges.get(0).payload);
                assertEquals(7, sent.pushedChanges.get(0).serverVersion);
    }

    @Test
    public void syncIfOnline_whenOnline_triggersSync() {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockSyncQueueDao.getPending())
                .thenReturn(new ArrayList<>());
        // This will attempt sync but may fail without full mock setup;
        // we just verify it's called
        try {
            syncManager.syncIfOnline();
        } catch (Exception e) {
            // Expected when REST mock isn't fully set up
        }
        verify(mockSyncQueueDao).resetInFlight();
    }

    @Test
    public void syncIfOnline_whenOffline_doesNothing() {
        when(mockNetworkMonitor.isOnline()).thenReturn(false);

        syncManager.syncIfOnline();

        verify(mockSyncQueueDao, never()).resetInFlight();
    }
}
