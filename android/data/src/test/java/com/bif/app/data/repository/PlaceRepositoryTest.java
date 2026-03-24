package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.location.Address;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.PlaceDto;
import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.data.source.local.PlaceDao;
import com.bif.app.data.source.local.SearchHistoryDao;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
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

public class PlaceRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule =
            new InstantTaskExecutorRule();

    @Mock
    private GoogleMapsDataSource mockGoogleMapsDataSource;
    @Mock
    private RestApiService mockRestApiService;
    @Mock
    private PlaceDao mockPlaceDao;
    @Mock
    private SearchHistoryDao mockSearchHistoryDao;
    @Mock
    private SyncManager mockSyncManager;
    @Mock
    private NetworkMonitor mockNetworkMonitor;

    private PlaceRepository placeRepository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        placeRepository = new PlaceRepository(
                mockGoogleMapsDataSource,
                mockRestApiService,
                mockPlaceDao,
                mockSearchHistoryDao,
                mockSyncManager,
                mockNetworkMonitor);
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    // --- searchLocation Tests ---

    @Test
    public void searchLocation_validQuery_returnsLocation()
            throws IOException, InterruptedException {
        Address mockAddress = mock(Address.class);
        when(mockAddress.getLatitude()).thenReturn(40.7128);
        when(mockAddress.getLongitude()).thenReturn(-74.0060);
        when(mockGoogleMapsDataSource.geocodeLocation("New York"))
                .thenReturn(Collections.singletonList(mockAddress));

        LiveData<Location> result =
                placeRepository.searchLocation("New York");
        Thread.sleep(200);

        assertNotNull(result);
        Location location = result.getValue();
        assertNotNull(location);
        assertEquals(40.7128, location.latitude, 0.0001);
        assertEquals(-74.0060, location.longitude, 0.0001);
    }

    @Test
    public void searchLocation_emptyResults_returnsNull()
            throws IOException, InterruptedException {
        when(mockGoogleMapsDataSource.geocodeLocation("Unknown"))
                .thenReturn(new ArrayList<>());

        LiveData<Location> result =
                placeRepository.searchLocation("Unknown");
        Thread.sleep(200);

        assertNull(result.getValue());
    }

    @Test
    public void searchLocation_ioException_returnsNull()
            throws IOException, InterruptedException {
        when(mockGoogleMapsDataSource.geocodeLocation("Error"))
                .thenThrow(new IOException("Geocode failed"));

        LiveData<Location> result =
                placeRepository.searchLocation("Error");
        Thread.sleep(200);

        assertNull(result.getValue());
    }

    // --- searchPlaces Tests ---

    @Test
    public void searchPlaces_nullQuery_returnsEmptyList() {
        LiveData<List<Place>> result = placeRepository.searchPlaces(null);
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void searchPlaces_emptyQuery_returnsEmptyList() {
        LiveData<List<Place>> result = placeRepository.searchPlaces("");
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_online_combinesServerAndGoogleResults()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        // Stub local cache (doSearch now queries this first)
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        // Server returns one place
        PlaceDto serverDto = new PlaceDto();
        serverDto.id = "server1";
        serverDto.name = "Server Place";
        serverDto.address = "123 Server St";
        serverDto.latitude = 10.0;
        serverDto.longitude = 20.0;

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(
                        Collections.singletonList(serverDto)));
        when(mockRestApiService.searchServerPlaces("test"))
                .thenReturn(mockCall);

        // Google returns one additional place
        Address googleAddr = mock(Address.class);
        when(googleAddr.getLatitude()).thenReturn(30.0);
        when(googleAddr.getLongitude()).thenReturn(40.0);
        when(googleAddr.getFeatureName()).thenReturn("Google Place");
        when(googleAddr.getAddressLine(0)).thenReturn("456 Google Ave");
        when(mockGoogleMapsDataSource.geocodeLocation("test"))
                .thenReturn(Collections.singletonList(googleAddr));

        // Mock the saveFromSearch call for Google-discovered place
        Call<PlaceDto> saveCall = mock(Call.class);
        when(saveCall.execute())
                .thenReturn(Response.success(new PlaceDto()));
        when(mockRestApiService.saveFromSearch(any(PlaceDto.class)))
                .thenReturn(saveCall);

        // Mock placeDao.count() to avoid eviction
        when(mockPlaceDao.count(anyString())).thenReturn(2);

        LiveData<List<Place>> result =
                placeRepository.searchPlaces("test");
        Thread.sleep(500);

        assertNotNull(result.getValue());
        assertEquals(2, result.getValue().size());
        assertEquals("server1", result.getValue().get(0).id);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_online_savesSearchHistory()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(new ArrayList<>()));
        when(mockRestApiService.searchServerPlaces("cafe"))
                .thenReturn(mockCall);
        when(mockGoogleMapsDataSource.geocodeLocation("cafe"))
                .thenReturn(new ArrayList<>());
        when(mockPlaceDao.count(anyString())).thenReturn(0);

        placeRepository.searchPlaces("cafe");
        Thread.sleep(300);

        verify(mockSearchHistoryDao).insert(any());
        verify(mockSearchHistoryDao).evictOldest();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlacesFromHistory_doesNotSaveHistory()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(new ArrayList<>()));
        when(mockRestApiService.searchServerPlaces("cafe"))
                .thenReturn(mockCall);
        when(mockGoogleMapsDataSource.geocodeLocation("cafe"))
                .thenReturn(new ArrayList<>());
        when(mockPlaceDao.count(anyString())).thenReturn(0);

        placeRepository.searchPlacesFromHistory("cafe");
        Thread.sleep(300);

        // History DAO should NOT be touched when replaying from history
        verify(mockSearchHistoryDao, never()).insert(any());
        verify(mockSearchHistoryDao, never()).evictOldest();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_online_enforcesLocalCacheLimit()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(new ArrayList<>()));
        when(mockRestApiService.searchServerPlaces("test"))
                .thenReturn(mockCall);
        when(mockGoogleMapsDataSource.geocodeLocation("test"))
                .thenReturn(new ArrayList<>());

        // Simulate 105 places in local cache
        when(mockPlaceDao.count(anyString())).thenReturn(105);

        placeRepository.searchPlaces("test");
        Thread.sleep(300);

        verify(mockPlaceDao).evictOldest(eq(5), anyString());
    }

    // --- persistPlace Tests ---

    @Test
    public void persistPlace_cachesLocallyAndEnqueuesSync()
            throws InterruptedException {
        Place place = new Place("p1", "Test", "Addr", 4.5,
                new Location(10.0, 20.0));
        when(mockPlaceDao.count(anyString())).thenReturn(50);

        placeRepository.persistPlace(place, "favorite");
        Thread.sleep(300);

        ArgumentCaptor<PlaceEntity> entityCaptor =
                ArgumentCaptor.forClass(PlaceEntity.class);
        verify(mockPlaceDao).upsert(entityCaptor.capture());
        assertEquals("favorite", entityCaptor.getValue().persistedByAction);

        verify(mockSyncManager).enqueueChange(
                anyString(), anyString(), anyString(), anyString(), any());
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void persistPlace_enforcesEvictionWhenOverLimit()
            throws InterruptedException {
        Place place = new Place("p1", "Test", "Addr", 4.5,
                new Location(10.0, 20.0));
        when(mockPlaceDao.count(anyString())).thenReturn(110);

        placeRepository.persistPlace(place, "review");
        Thread.sleep(300);

        verify(mockPlaceDao).evictOldest(eq(10), anyString());
    }
}
