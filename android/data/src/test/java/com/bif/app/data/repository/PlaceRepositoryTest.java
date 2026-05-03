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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.location.Address;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.AiGraphQlClient;
import com.bif.app.core.network.dto.ai.AiPlaceSuggestionPayload;
import com.bif.app.core.network.dto.ai.AiSuggestedPlacePayload;
import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.core.network.dto.place.PlaceSearchRequestDTO;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
        
        // Create a synchronous executor for testing
        ExecutorService testExecutor = new ExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run(); // Run synchronously on test thread
            }
            @Override
            public void shutdown() {}
            @Override
            public List<Runnable> shutdownNow() { return Collections.emptyList(); }
            @Override
            public boolean isShutdown() { return false; }
            @Override
            public boolean isTerminated() { return false; }
            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
            @Override
            public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) { return null; }
            @Override
            public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) { return null; }
            @Override
            public java.util.concurrent.Future<?> submit(Runnable task) { return null; }
            @Override
            public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { return null; }
            @Override
            public <T> List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { return null; }
            @Override
            public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) { return null; }
            @Override
            public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, TimeUnit unit) { return null; }
        };
        
        placeRepository = new PlaceRepository(
                mockGeocodingDataSource,
                mockRestApiService,
                mockPlaceDao,
                mockSearchHistoryDao,
                mockSyncManager,
                mockNetworkMonitor,
                mockAiGraphQlClient,
                "anonymous",
                testExecutor);
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
        LiveData<List<Place>> result = placeRepository.searchPlaces(null, null);
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void searchPlaces_emptyQuery_returnsEmptyList() {
        LiveData<List<Place>> result = placeRepository.searchPlaces("", null);
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_withUserLocation_passesLatLngToApi()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute())
                .thenReturn(Response.success(new ArrayList<>()));
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
                .thenReturn(mockCall);
        when(mockGeocodingDataSource.geocodeLocation("test"))
                .thenReturn(new ArrayList<>());
        when(mockPlaceDao.count(anyString())).thenReturn(0);

        placeRepository.searchPlaces("test", new Location(10.5, 106.7));
        Thread.sleep(300);

        ArgumentCaptor<PlaceSearchRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(PlaceSearchRequestDTO.class);
        verify(mockRestApiService).searchServerPlaces(requestCaptor.capture());
        assertEquals("test", requestCaptor.getValue().query);
        assertEquals(Double.valueOf(10.5), requestCaptor.getValue().latitude);
        assertEquals(Double.valueOf(106.7), requestCaptor.getValue().longitude);
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
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Place>> resultValue = new AtomicReference<>();
        
        // Create a valid user location to trigger server search
        Location userLocation = new Location();
        userLocation.latitude = 10.0;
        userLocation.longitude = 20.0;
        
        LiveData<List<Place>> result =
                placeRepository.searchPlaces("test", userLocation);
        
        // Use Observer to wait for the result
        Observer<List<Place>> observer = places -> {
            resultValue.set(places);
            latch.countDown();
        };
        result.observeForever(observer);
        
        // Wait for the async operation to complete (max 5 seconds)
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        result.removeObserver(observer);
        
        assertTrue("Search did not complete within timeout", completed);
        assertNotNull("result value should not be null", resultValue.get());
        List<Place> places = resultValue.get();
        assertEquals("Expected exactly 2 results but got " + places.size(), 
            2, places.size());
        assertEquals("server1", places.get(0).id);
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
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
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

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Place>> resultValue = new AtomicReference<>();
        
        // Create a valid user location to trigger server search
        Location userLocation = new Location();
        userLocation.latitude = 10.0;
        userLocation.longitude = 20.0;
        
        LiveData<List<Place>> result = placeRepository.searchPlaces("museum", userLocation);
        
        // Use Observer to wait for the result
        Observer<List<Place>> observer = places -> {
            resultValue.set(places);
            latch.countDown();
        };
        result.observeForever(observer);
        
        // Wait for the async operation to complete (max 5 seconds)
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        result.removeObserver(observer);
        
        assertTrue("Search did not complete within timeout", completed);
        assertNotNull("result value should not be null", resultValue.get());
        assertEquals("Expected exactly 3 results", 3, resultValue.get().size());
        assertEquals("server1", resultValue.get().get(0).id);
        assertEquals("geocode_30.0_40.0", resultValue.get().get(1).id);
        assertEquals("local1", resultValue.get().get(2).id);
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
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
                .thenReturn(mockCall);
        when(mockGeocodingDataSource.geocodeLocation("cafe"))
                .thenReturn(new ArrayList<>());
        when(mockPlaceDao.count(anyString())).thenReturn(0);

        placeRepository.searchPlaces("cafe", null);
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
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
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
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
                .thenReturn(mockCall);
        when(mockGeocodingDataSource.geocodeLocation("test"))
                .thenReturn(new ArrayList<>());

        // Simulate 505 places in local cache
        when(mockPlaceDao.count(anyString())).thenReturn(505);

        placeRepository.searchPlaces("test", null);
        Thread.sleep(300);

        verify(mockPlaceDao, timeout(1500)).evictOldest(eq(5), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_online_reordersFarFirstBackendResultsByProximity()
            throws IOException, InterruptedException {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockPlaceDao.searchByName(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        PlaceDto farDto = new PlaceDto();
        farDto.id = "server_far";
        farDto.name = "Far Place";
        farDto.address = "Far Address";
        farDto.latitude = 21.0278;   // Hanoi
        farDto.longitude = 105.8342;

        PlaceDto nearDto = new PlaceDto();
        nearDto.id = "server_near";
        nearDto.name = "Near Place";
        nearDto.address = "Near Address";
        nearDto.latitude = 10.7750;  // Ho Chi Minh City center-ish
        nearDto.longitude = 106.7000;

        // Simulate backend returning farther place first.
        List<PlaceDto> backendOrder = new ArrayList<>();
        backendOrder.add(farDto);
        backendOrder.add(nearDto);

        Call<List<PlaceDto>> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(backendOrder));
        when(mockRestApiService.searchServerPlaces(any(PlaceSearchRequestDTO.class)))
                .thenReturn(mockCall);
        when(mockGeocodingDataSource.geocodeLocation("coffee"))
                .thenReturn(new ArrayList<>());
        when(mockPlaceDao.count(anyString())).thenReturn(2);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Place>> resultValue = new AtomicReference<>();

        LiveData<List<Place>> result = placeRepository.searchPlaces(
                "coffee",
                new Location(10.7769, 106.7009));

        Observer<List<Place>> observer = places -> {
            resultValue.set(places);
            latch.countDown();
        };
        result.observeForever(observer);

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        result.removeObserver(observer);

        assertTrue("Search did not complete within timeout", completed);
        assertNotNull("result value should not be null", resultValue.get());
        assertEquals(2, resultValue.get().size());
        assertEquals("server_near", resultValue.get().get(0).id);
        assertEquals("server_far", resultValue.get().get(1).id);
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
    public void suggestPlacesFromQuery_nullAiClient_returnsAiFailureWithWarning() {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        PlaceRepository repositoryWithoutAiClient = new PlaceRepository(
                mockGeocodingDataSource,
                mockRestApiService,
                mockPlaceDao,
                mockSearchHistoryDao,
                mockSyncManager,
                mockNetworkMonitor,
                null,
                null);

        LiveData<AiPlaceSuggestionResult> result =
                repositoryWithoutAiClient.suggestPlacesFromQuery("ramen");

        assertNotNull(result.getValue());
        assertEquals("AI_FAILURE", result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
        assertTrue(result.getValue().getWarnings().stream()
                .anyMatch(message -> message.contains("AI client is unavailable")));
    }

    @Test
    public void suggestPlacesFromQuery_transportFailure_surfacesWarning()
            throws Exception {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);

        CompletableFuture<AiPlaceSuggestionPayload> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("suggestPlacesFromQuery timed out"));
        when(mockAiGraphQlClient.suggestPlacesFromQuery(
                org.mockito.Mockito.eq("best coffee"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(failedFuture);

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("best coffee");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        assertEquals("AI_FAILURE", result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
        assertTrue(result.getValue().getWarnings().stream()
                .anyMatch(message -> message.contains("Transport error")));
    }

    @Test
    public void suggestPlacesFromQuery_withBiasForwardsContextToGraphQlClient()
            throws Exception {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery(
                org.mockito.Mockito.eq("coffee near center"),
                org.mockito.Mockito.eq(10.7769),
                org.mockito.Mockito.eq(106.7009),
                org.mockito.Mockito.eq("District 1, Ho Chi Minh City")))
                .thenReturn(CompletableFuture.completedFuture(
                        new AiPlaceSuggestionPayload(
                                Collections.emptyList(),
                                Collections.emptyList(),
                                null)));

        LiveData<AiPlaceSuggestionResult> result = placeRepository.suggestPlacesFromQuery(
                "coffee near center",
                10.7769,
                106.7009,
                "District 1, Ho Chi Minh City");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        verify(mockAiGraphQlClient).suggestPlacesFromQuery(
                "coffee near center",
                10.7769,
                106.7009,
                "District 1, Ho Chi Minh City");
    }

    @Test
    public void suggestPlacesFromQuery_failureCode_doesNotReturnPlaces()
            throws Exception {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery(
                org.mockito.Mockito.eq("late night food"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AiPlaceSuggestionPayload(
                                Collections.emptyList(),
                                Collections.singletonList("warning"),
                                "RATE_LIMITED")));

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("late night food");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        assertEquals("RATE_LIMITED", result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
    }

    @Test
    public void suggestPlacesFromQuery_success_mapsPlaces()
            throws Exception {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery(
                org.mockito.Mockito.eq("best coffee"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AiPlaceSuggestionPayload(
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
                                null)));

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
    public void suggestPlacesFromQuery_incompletePlace_isFilteredOut()
            throws Exception {
        when(mockNetworkMonitor.isOnline()).thenReturn(true);
        when(mockAiGraphQlClient.suggestPlacesFromQuery(
                org.mockito.Mockito.eq("missing fields"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(CompletableFuture.completedFuture(
                        new AiPlaceSuggestionPayload(
                                Collections.singletonList(
                                        new AiSuggestedPlacePayload(
                                                null,
                                                "",
                                                "101 Bean St",
                                                4.2,
                                                2,
                                                null,
                                                106.22)),
                                Collections.emptyList(),
                                null)));

        LiveData<AiPlaceSuggestionResult> result =
                placeRepository.suggestPlacesFromQuery("missing fields");
        Thread.sleep(250);

        assertNotNull(result.getValue());
        assertNull(result.getValue().getFailureCode());
        assertTrue(result.getValue().getPlaces().isEmpty());
        assertTrue(result.getValue().getWarnings().stream()
                .anyMatch(message -> message.contains("filtered out")));
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
    public void persistPlace_viewedWithMissingAddress_usesPlaceholderAndDoesNotQueueSync()
            throws InterruptedException {
        Place place = new Place("p2", "Clicked Place", "", 0,
                new Location(1.23, 4.56));
        when(mockPlaceDao.count(anyString())).thenReturn(10);
        long before = System.currentTimeMillis();

        placeRepository.persistPlace(place, "viewed");
        Thread.sleep(300);

        ArgumentCaptor<PlaceEntity> entityCaptor =
                ArgumentCaptor.forClass(PlaceEntity.class);
        verify(mockPlaceDao).upsert(entityCaptor.capture());
        assertEquals("Address unavailable", entityCaptor.getValue().address);
        assertTrue(entityCaptor.getValue().viewedAt >= before);
        assertTrue(entityCaptor.getValue().viewedAt <= System.currentTimeMillis());

        verify(mockSyncManager, never()).enqueueChange(eq("place"), anyString(),
                anyString(), anyString(), any());
        verify(mockSyncManager, never()).syncIfOnline();
    }

    @Test
    public void persistPlace_reviewAction_doesNotStampViewedAt()
            throws InterruptedException {
        Place place = new Place("p3", "Review Place", "Review Address", 0,
                new Location(7.89, 1.23));
        when(mockPlaceDao.count(anyString())).thenReturn(10);

        placeRepository.persistPlace(place, "review");
        Thread.sleep(300);

        ArgumentCaptor<PlaceEntity> entityCaptor =
                ArgumentCaptor.forClass(PlaceEntity.class);
        verify(mockPlaceDao).upsert(entityCaptor.capture());
        assertEquals(0L, entityCaptor.getValue().viewedAt);
        verify(mockSyncManager).enqueueChange(eq("place"), eq("p3"), eq("CREATE"), anyString(), any());
    }

    @Test
    public void persistPlace_withoutId_generatesDeterministicId()
            throws InterruptedException {
        Place place = new Place(null, "Clicked Place", "Clicked Address", 0,
                new Location(1.23, 4.56));
        when(mockPlaceDao.count(anyString())).thenReturn(10);

        placeRepository.persistPlace(place, "viewed");
        placeRepository.persistPlace(place, "viewed");
        Thread.sleep(300);

        ArgumentCaptor<PlaceEntity> entityCaptor =
                ArgumentCaptor.forClass(PlaceEntity.class);
        verify(mockPlaceDao, times(2)).upsert(entityCaptor.capture());

        assertEquals(entityCaptor.getAllValues().get(0).id,
                entityCaptor.getAllValues().get(1).id);
        verify(mockSyncManager, never()).enqueueChange(eq("place"), anyString(),
                anyString(), anyString(), any());
        verify(mockSyncManager, never()).syncIfOnline();
    }
}
