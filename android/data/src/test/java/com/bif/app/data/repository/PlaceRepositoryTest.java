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
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.location.Address;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.AiGraphQlClient;
import com.bif.app.core.network.dto.ai.AiPlaceSuggestionPayload;
import com.bif.app.core.network.dto.ai.AiSuggestedPlacePayload;
import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.data.source.AndroidGeocodingDataSource;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.SearchHistoryDao;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
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
        private AndroidGeocodingDataSource mockGeocodingDataSource;
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
        @Mock
        private AiGraphQlClient mockAiGraphQlClient;

    private PlaceRepository placeRepository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        placeRepository = new PlaceRepository(
                mockGeocodingDataSource,
                mockRestApiService,
                mockPlaceDao,
                mockSearchHistoryDao,
                mockSyncManager,
                mockNetworkMonitor,
                mockAiGraphQlClient,
                null);
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
        when(mockGeocodingDataSource.geocodeLocation("New York"))
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
        when(mockGeocodingDataSource.geocodeLocation("Unknown"))
                .thenReturn(new ArrayList<>());

        LiveData<Location> result =
                placeRepository.searchLocation("Unknown");
        Thread.sleep(200);

        assertNull(result.getValue());
    }

    @Test
    public void searchLocation_ioException_returnsNull()
            throws IOException, InterruptedException {
        when(mockGeocodingDataSource.geocodeLocation("Error"))
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
        public void searchPlaces_online_combinesServerAndGeocoderResults()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
                // Local SQLite query now runs last and can still contribute more results.
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

        // Geocoder returns one additional place
        Address googleAddr = mock(Address.class);
        when(googleAddr.getLatitude()).thenReturn(30.0);
        when(googleAddr.getLongitude()).thenReturn(40.0);
        when(googleAddr.getFeatureName()).thenReturn("OSM Place");
        when(googleAddr.getAddressLine(0)).thenReturn("456 OSM Ave");
        when(mockGeocodingDataSource.geocodeLocation("test"))
                .thenReturn(Collections.singletonList(googleAddr));

        // Mock saveFromSearch call for geocoder-discovered place
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
    public void searchPlaces_online_prioritizesServerThenGeocoderThenLocal()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        PlaceDto serverDto = new PlaceDto();
        serverDto.id = "server1";
        serverDto.name = "Server Place";
        serverDto.address = "123 Server St";
        serverDto.latitude = 10.0;
        serverDto.longitude = 20.0;

        Call<List<PlaceDto>> searchCall = mock(Call.class);
        when(searchCall.execute())
                .thenReturn(Response.success(Collections.singletonList(serverDto)));
        when(mockRestApiService.searchServerPlaces("museum"))
                .thenReturn(searchCall);

        Address osmAddr = mock(Address.class);
        when(osmAddr.getLatitude()).thenReturn(30.0);
        when(osmAddr.getLongitude()).thenReturn(40.0);
        when(osmAddr.getFeatureName()).thenReturn("OSM Place");
        when(osmAddr.getAddressLine(0)).thenReturn("456 OSM Ave");
        when(mockGeocodingDataSource.geocodeLocation("museum"))
                .thenReturn(Collections.singletonList(osmAddr));

        PlaceEntity local = new PlaceEntity();
        local.ownerUserId = "anonymous";
        local.id = "local1";
        local.name = "Local Place";
        local.address = "789 Local St";
        local.latitude = 50.0;
        local.longitude = 60.0;
        local.deleted = false;
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(Collections.singletonList(local));

        Call<PlaceDto> saveCall = mock(Call.class);
        when(saveCall.execute()).thenReturn(Response.success(new PlaceDto()));
        when(mockRestApiService.saveFromSearch(any(PlaceDto.class)))
                .thenReturn(saveCall);

        when(mockPlaceDao.count(anyString())).thenReturn(3);

        LiveData<List<Place>> result = placeRepository.searchPlaces("museum");
        Thread.sleep(600);

        assertNotNull(result.getValue());
        assertEquals(3, result.getValue().size());
        assertEquals("server1", result.getValue().get(0).id);
        assertEquals("geocode_30.0_40.0", result.getValue().get(1).id);
        assertEquals("local1", result.getValue().get(2).id);
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
        when(mockGeocodingDataSource.geocodeLocation("cafe"))
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
        when(mockGeocodingDataSource.geocodeLocation("cafe"))
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
        when(mockGeocodingDataSource.geocodeLocation("test"))
                .thenReturn(new ArrayList<>());

        // Simulate 505 places in local cache
        when(mockPlaceDao.count(anyString())).thenReturn(505);

        placeRepository.searchPlaces("test");
        Thread.sleep(300);

        verify(mockPlaceDao, timeout(1500)).evictOldest(eq(5), anyString());
    }

    // --- persistPlace Tests ---

    @Test
    public void suggestPlacesFromQuery_offline_returnsOfflineFailureCode() {
        when(mockNetworkMonitor.isOnline()).thenReturn(false);

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("ramen");

        assertNotNull(result.getValue());
        assertEquals("OFFLINE", result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
    }

    @Test
    public void suggestPlacesFromQuery_failureCode_doesNotReturnPlaces()
            throws Exception, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery("late night food"))
                .thenReturn(new AiPlaceSuggestionPayload(
                        Collections.emptyList(),
                        Collections.singletonList("warning"),
                        "RATE_LIMITED"));

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("late night food");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        assertEquals("RATE_LIMITED", result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
    }

    @Test
    public void suggestPlacesFromQuery_success_mapsPlaces()
            throws Exception, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery("best coffee"))
                .thenReturn(new AiPlaceSuggestionPayload(
                        Collections.singletonList(
                                new AiSuggestedPlacePayload(
                                        "p-ai-1",
                                        "Morning Brew",
                                        "101 Bean St",
                                        4.7,
                                        14,
                                        10.11,
                                        106.22)),
                        Collections.singletonList("minor"),
                        null));

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("best coffee");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        assertNull(result.getValue().getFailureCode());
        assertEquals(1, result.getValue().getPlaces().size());
        assertEquals("Morning Brew",
                result.getValue().getPlaces().get(0).getPlace().name);
        assertEquals(14,
                result.getValue().getPlaces().get(0).getAddedToTripCount());
    }

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
        when(mockPlaceDao.count(anyString())).thenReturn(510);

        placeRepository.persistPlace(place, "review");
        Thread.sleep(300);

        verify(mockPlaceDao, timeout(1500)).evictOldest(eq(10), anyString());
    }

    @Test
    public void persistPlace_viewedWithMissingAddress_usesPlaceholderAndQueuesSync()
            throws InterruptedException {
        Place place = new Place("p2", "Clicked Place", "", 0,
                new Location(1.23, 4.56));
        when(mockPlaceDao.count(anyString())).thenReturn(10);

        placeRepository.persistPlace(place, "viewed");
        Thread.sleep(300);

        ArgumentCaptor<PlaceEntity> entityCaptor =
                ArgumentCaptor.forClass(PlaceEntity.class);
        verify(mockPlaceDao).upsert(entityCaptor.capture());
        assertEquals("Address unavailable", entityCaptor.getValue().address);

        verify(mockSyncManager).enqueueChange(eq("place"), eq("p2"),
                anyString(), anyString(), any());
    }
}


