package com.bif.app.data.sync;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.sync.handler.TripStopSyncEntityHandler;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TripStopSyncEntityHandlerTest {

    @Mock
    private TripDao mockTripDao;

    private TripStopSyncEntityHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new TripStopSyncEntityHandler(mockTripDao, new Gson());
    }

    @Test
    public void applyPulledChange_deleteWithoutPayload_marksDeleted() {
        TripStopEntity existing = new TripStopEntity();
        existing.id = "stop-1";
        existing.tripId = "trip-1";
        when(mockTripDao.getStopByIdSync("stop-1")).thenReturn(existing);

        SyncChangeDto change = new SyncChangeDto();
        change.entityId = "stop-1";
        change.operation = "DELETE";
        change.serverVersion = 7L;

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<TripStopEntity> captor = ArgumentCaptor.forClass(TripStopEntity.class);
        verify(mockTripDao).upsertStop(captor.capture());
        org.junit.Assert.assertTrue(captor.getValue().deleted);
        org.junit.Assert.assertEquals(7L, captor.getValue().serverVersion);
    }

    @Test
    public void applyPulledChange_payloadUpsertsStopFields() {
        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.serverVersion = 10L;
        change.payload = "{\"id\":\"stop-1\",\"tripId\":\"trip-1\","
                + "\"title\":\"Museum\",\"note\":\"visit\","
                + "\"location\":{\"latitude\":10.5,\"longitude\":20.25},"
                + "\"arrivalTime\":\"2026-03-28T09:00:00Z\","
                + "\"departureTime\":\"2026-03-28T10:00:00Z\","
                + "\"orderIndex\":2,\"serverVersion\":9,\"deleted\":false}";

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<TripStopEntity> captor = ArgumentCaptor.forClass(TripStopEntity.class);
        verify(mockTripDao).upsertStop(captor.capture());
        TripStopEntity saved = captor.getValue();
        org.junit.Assert.assertEquals("stop-1", saved.id);
        org.junit.Assert.assertEquals("trip-1", saved.tripId);
        org.junit.Assert.assertEquals("Museum", saved.title);
        org.junit.Assert.assertEquals(2, saved.orderIndex);
        org.junit.Assert.assertEquals(10L, saved.serverVersion);
    }

    @Test
    public void applyPulledChange_keepsPendingLocalUploadWhenRemotePhotoArrives() {
        TripStopEntity existing = new TripStopEntity();
        existing.id = "stop-2";
        existing.localImagePath = "/files/image-staging/stop-2.jpg";
        existing.uploadStatus = UploadStatus.ERROR;
        when(mockTripDao.getStopByIdSync("stop-2")).thenReturn(existing);

        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.serverVersion = 22L;
        change.payload = "{\"id\":\"stop-2\",\"tripId\":\"trip-1\","
                + "\"photoUrl\":\"https://cdn.example.com/stop-2.jpg\","
                + "\"serverVersion\":22,\"deleted\":false}";

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<TripStopEntity> captor = ArgumentCaptor.forClass(TripStopEntity.class);
        verify(mockTripDao).upsertStop(captor.capture());
        TripStopEntity saved = captor.getValue();
        org.junit.Assert.assertEquals("/files/image-staging/stop-2.jpg",
                saved.localImagePath);
        org.junit.Assert.assertEquals(UploadStatus.ERROR, saved.uploadStatus);
    }
}
