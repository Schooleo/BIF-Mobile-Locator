package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.source.local.TripDao;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

public class TripRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Mock
    private TripDao mockTripDao;
    @Mock
    private SyncManager mockSyncManager;

    private TripRepository tripRepository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        tripRepository = new TripRepository(mockTripDao, mockSyncManager);
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void getTripsByGroup_mapsFromRoomRelation() {
        MutableLiveData<List<TripDao.TripPlanWithStops>> source =
                new MutableLiveData<>();

        TripPlanEntity trip = new TripPlanEntity();
        trip.id = "trip-1";
        trip.groupId = "group-1";
        trip.title = "Weekend";
        trip.description = "Road trip";
        trip.startAt = 1000L;
        trip.endAt = 2000L;

        TripStopEntity first = new TripStopEntity();
        first.id = "s1";
        first.tripId = "trip-1";
        first.title = "A";
        first.orderIndex = 1;

        TripStopEntity second = new TripStopEntity();
        second.id = "s2";
        second.tripId = "trip-1";
        second.title = "B";
        second.orderIndex = 0;

        TripDao.TripPlanWithStops item = new TripDao.TripPlanWithStops();
        item.trip = trip;
        item.stops = new ArrayList<>(List.of(first, second));

        source.setValue(List.of(item));
        when(mockTripDao.getTripsWithStopsByGroup("group-1"))
                .thenReturn(source);

        LiveData<List<TripPlan>> liveData =
                tripRepository.getTripsByGroup("group-1");

        assertNotNull(liveData.getValue());
        assertEquals(1, liveData.getValue().size());
        TripPlan mapped = liveData.getValue().get(0);
        assertEquals("trip-1", mapped.getId());
        // Stops should be ordered by orderIndex ascending.
        assertEquals("B", mapped.getStops().get(0).getTitle());
        assertEquals("A", mapped.getStops().get(1).getTitle());
    }

    @Test
    public void addStopToTrip_upsertsLocallyAndEnqueuesSync()
            throws InterruptedException {
        when(mockTripDao.getStopByIdSync("stop-1")).thenReturn(null);
        when(mockTripDao.getActiveStopsByTripSync("trip-1"))
                .thenReturn(List.of(new TripStopEntity(), new TripStopEntity()));

        TripStop stop = new TripStop("stop-1", "Museum", "note",
                1.0, 2.0, 0L, 0L, 0);

        tripRepository.addStopToTrip("trip-1", stop);
        Thread.sleep(200);

        ArgumentCaptor<TripStopEntity> captor =
                ArgumentCaptor.forClass(TripStopEntity.class);
        verify(mockTripDao).upsertStop(captor.capture());
        assertEquals("stop-1", captor.getValue().id);
        assertEquals(2, captor.getValue().orderIndex);

        verify(mockSyncManager).enqueueChange(eq("trip_stop"),
                eq("stop-1"), eq("UPDATE"), anyString(), any());
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void removeStopFromTrip_marksDeletedAndReindexesActive()
            throws InterruptedException {
        TripStopEntity removed = new TripStopEntity();
        removed.id = "s2";
        removed.tripId = "trip-1";
        removed.orderIndex = 1;

        TripStopEntity active0 = new TripStopEntity();
        active0.id = "s1";
        active0.tripId = "trip-1";
        active0.orderIndex = 0;

        TripStopEntity active1 = new TripStopEntity();
        active1.id = "s3";
        active1.tripId = "trip-1";
        active1.orderIndex = 5;

        when(mockTripDao.getStopByIdSync("s2")).thenReturn(removed);
        when(mockTripDao.getActiveStopsByTripSync("trip-1"))
                .thenReturn(List.of(active0, active1));

        tripRepository.removeStopFromTrip("trip-1", "s2");
        Thread.sleep(250);

        verify(mockTripDao, atLeastOnce()).upsertStop(any());
        verify(mockSyncManager, atLeastOnce()).enqueueChange(eq("trip_stop"),
                anyString(), eq("UPDATE"), anyString(), any());
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void rearrangeStopsInTrip_rewritesOrderAndEnqueuesEach()
            throws InterruptedException {
        when(mockTripDao.getStopByIdSync(anyString())).thenReturn(null);

        List<TripStop> newStops = List.of(
                new TripStop("s1", "A", "", 0, 0, 0, 0, 9),
                new TripStop("s2", "B", "", 0, 0, 0, 0, 9)
        );

        tripRepository.rearrangeStopsInTrip("trip-1", newStops);
        Thread.sleep(250);

        ArgumentCaptor<TripStopEntity> captor =
                ArgumentCaptor.forClass(TripStopEntity.class);
        verify(mockTripDao, atLeastOnce()).upsertStop(captor.capture());
        List<TripStopEntity> saved = captor.getAllValues();
        assertEquals("s1", saved.get(0).id);
        assertEquals(0, saved.get(0).orderIndex);
        assertEquals("s2", saved.get(1).id);
        assertEquals(1, saved.get(1).orderIndex);

        verify(mockSyncManager).enqueueChange(eq("trip_stop"), eq("s1"),
                eq("UPDATE"), anyString(), any());
        verify(mockSyncManager).enqueueChange(eq("trip_stop"), eq("s2"),
                eq("UPDATE"), anyString(), any());
    }

    @Test
    public void refreshTrips_triggersSyncOnly() throws InterruptedException {
        MutableLiveData<List<TripDao.TripPlanWithStops>> source =
                new MutableLiveData<>();
        source.setValue(new ArrayList<>());
        when(mockTripDao.getTripsWithStopsByGroup(anyString())).thenReturn(source);

        tripRepository.refreshTrips("group-1");
        Thread.sleep(150);

        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void getTripsByGroup_filtersDeletedEntities() {
        MutableLiveData<List<TripDao.TripPlanWithStops>> source =
                new MutableLiveData<>();

        TripPlanEntity deletedTrip = new TripPlanEntity();
        deletedTrip.id = "trip-del";
        deletedTrip.groupId = "group-1";
        deletedTrip.deleted = true;

        TripDao.TripPlanWithStops item = new TripDao.TripPlanWithStops();
        item.trip = deletedTrip;
        item.stops = new ArrayList<>();
        source.setValue(List.of(item));

        when(mockTripDao.getTripsWithStopsByGroup("group-1"))
                .thenReturn(source);

        LiveData<List<TripPlan>> liveData =
                tripRepository.getTripsByGroup("group-1");

        assertNotNull(liveData.getValue());
        assertEquals(0, liveData.getValue().size());
    }

    @Test
    public void getTripsByGroup_handlesNullRoomValue() {
        MutableLiveData<List<TripDao.TripPlanWithStops>> source =
                new MutableLiveData<>();
        source.setValue(null);
        when(mockTripDao.getTripsWithStopsByGroup("group-1"))
                .thenReturn(source);

        LiveData<List<TripPlan>> liveData =
                tripRepository.getTripsByGroup("group-1");

        assertNotNull(liveData.getValue());
        assertEquals(0, liveData.getValue().size());
    }
}
