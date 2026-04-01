package com.bif.app.data.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.TripDao;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TripSyncEntityHandlerTest {

    @Mock
    private TripDao mockTripDao;

    private TripSyncEntityHandler handler;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        handler = new TripSyncEntityHandler(mockTripDao, new Gson());
    }

    @Test
    public void applyPulledChange_deleteWithoutPayload_marksDeleted() {
        TripPlanEntity existing = new TripPlanEntity();
        existing.id = "trip-1";
        when(mockTripDao.getTripByIdSync("trip-1")).thenReturn(existing);

        SyncChangeDto change = new SyncChangeDto();
        change.entityId = "trip-1";
        change.operation = "DELETE";
        change.serverVersion = 14L;
        change.payload = null;

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<TripPlanEntity> captor =
                ArgumentCaptor.forClass(TripPlanEntity.class);
        verify(mockTripDao).upsertTrip(captor.capture());
        org.junit.Assert.assertTrue(captor.getValue().deleted);
        org.junit.Assert.assertEquals(14L, captor.getValue().serverVersion);
    }

    @Test
    public void applyPulledChange_payloadUpsertsTripFields() {
        when(mockTripDao.countActiveTripsByGroup("g-1")).thenReturn(10);

        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.serverVersion = 9L;
        change.payload = "{\"id\":\"trip-1\",\"groupId\":\"g-1\","
                + "\"title\":\"Title\",\"description\":\"Desc\","
                + "\"startAt\":\"2026-03-28T10:00:00Z\","
                + "\"endAt\":\"2026-03-28T12:00:00Z\","
                + "\"serverVersion\":8,\"deleted\":false}";

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<TripPlanEntity> captor =
                ArgumentCaptor.forClass(TripPlanEntity.class);
        verify(mockTripDao).upsertTrip(captor.capture());

        TripPlanEntity saved = captor.getValue();
        org.junit.Assert.assertEquals("trip-1", saved.id);
        org.junit.Assert.assertEquals("g-1", saved.groupId);
        org.junit.Assert.assertEquals("Title", saved.title);
        org.junit.Assert.assertEquals(9L, saved.serverVersion);
        verify(mockTripDao).countActiveTripsByGroup("g-1");
        verify(mockTripDao, never()).evictOldestTripsByGroup(any(), anyInt());
    }

    @Test
    public void applyPulledChange_whenGroupExceedsCap_evictsOldestTrips() {
        when(mockTripDao.countActiveTripsByGroup("g-1")).thenReturn(13);

        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.serverVersion = 9L;
        change.payload = "{\"id\":\"trip-11\",\"groupId\":\"g-1\","
                + "\"title\":\"Overflow\",\"description\":\"D\"}";

        handler.applyPulledChange(change, "user-1");

        verify(mockTripDao).evictOldestTripsByGroup("g-1", 3);
    }
}

