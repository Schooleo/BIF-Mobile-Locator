package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.PlaceIdentityContext;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IPlaceRepository.PersistenceCallback;
import com.bif.app.domain.repository.IReviewRepository;
import com.bif.app.domain.repository.IRouteRepository;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class MapViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private IPlaceRepository placeRepository;

    @Mock
    private IMapRepository mapRepository;

    @Mock
    private IFavoriteRepository favoriteRepository;

    @Mock
    private IGroupRepository groupRepository;

    @Mock
    private IRouteRepository routeRepository;

    @Mock
    private IReviewRepository reviewRepository;

    @Mock
    private Observer<Location> searchResultObserver;

    @Mock
    private Observer<Event<String>> statusTextObserver;

    private Executor directExecutor;

    private MapViewModel viewModel;

    private static class QueueExecutor implements Executor {
        final java.util.List<Runnable> tasks = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }
    }

    @Before
    public void setUp() {
        directExecutor = Runnable::run;

        // Lenient: these guard switchMap setup paths but not every test exercises them.
        Mockito.lenient().when(placeRepository.searchLocation(ArgumentMatchers.anyString()))
            .thenReturn(new MutableLiveData<>());
        Mockito.lenient().when(placeRepository.searchPlacesFromHistory(ArgumentMatchers.anyString()))
            .thenReturn(new MutableLiveData<>());
        Mockito.lenient().when(groupRepository.getGroups())
            .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        Mockito.lenient().when(routeRepository.getRoute(ArgumentMatchers.anyList()))
            .thenReturn(new MutableLiveData<>());
        Mockito.lenient().when(reviewRepository.getReviewsForPlace(ArgumentMatchers.anyString()))
            .thenReturn(new MutableLiveData<>());
        Mockito.lenient().when(reviewRepository.getMyReview(ArgumentMatchers.anyString()))
            .thenReturn(new MutableLiveData<>());
        Mockito.lenient().doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            if (callback != null) {
                callback.run();
            }
            return null;
        }).when(reviewRepository).refreshReviews(ArgumentMatchers.anyString(), ArgumentMatchers.any());

        viewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                directExecutor);
        viewModel.searchResult.observeForever(searchResultObserver);
        viewModel.statusText.observeForever(statusTextObserver);
    }

    @Test
    public void searchLocation_validQuery_callsRepository() {
        // Arrange
        String query = "New York";

        // Act
        viewModel.searchLocation(query);

        // Assert
        Mockito.verify(placeRepository).searchLocation(query);
    }

    @Test
    public void setStatusText_updatesLiveData() {
        // Arrange
        String status = "Loading...";

        // Act
        viewModel.setStatusText(status);

        // Assert - statusText is now LiveData<Event<String>>
        Event<String> event = viewModel.statusText.getValue();
        assertNotNull(event);
        assertEquals(status, event.peekContent());
    }

    @Test
    public void clearPendingStatusText_clearsLiveDataValue() {
        viewModel.setStatusText("Old message");
        assertNotNull(viewModel.statusText.getValue());

        viewModel.clearPendingStatusText();

        assertNull(viewModel.statusText.getValue());
    }

    @Test
    public void saveMapState_validInput_callsRepository() {
        // Arrange
        double lat = 10.0;
        double lng = 20.0;
        float zoom = 15.0f;

        // Act
        viewModel.saveMapState(lat, lng, zoom);

        // Assert
        ArgumentCaptor<MapState> captor = ArgumentCaptor.forClass(MapState.class);
        Mockito.verify(mapRepository).saveMapState(captor.capture());

        MapState savedState = captor.getValue();
        assertEquals(lat, savedState.latitude, 0.001);
        assertEquals(lng, savedState.longitude, 0.001);
        assertEquals(zoom, savedState.zoomLevel, 0.001);
    }

    @Test
    public void getLastMapState_repositoryReturnsState_returnsSameState() {
        // Arrange
        MapState expectedState = new MapState(10.0, 20.0, 15.0f);
        Mockito.when(mapRepository.getMapState()).thenReturn(expectedState);

        // Act
        MapState result = viewModel.getLastMapState();

        // Assert
        assertNotNull(result);
        assertEquals(expectedState, result);
    }

    @Test
    public void saveMapState_verifiesDataIntegrity() {
        // Arrange
        double expectedLat = 10.762622;
        double expectedLng = 106.682311;
        float expectedZoom = 15.5f;

        // Act
        viewModel.saveMapState(expectedLat, expectedLng, expectedZoom);

        // Assert
        ArgumentCaptor<MapState> captor = ArgumentCaptor.forClass(MapState.class);
        Mockito.verify(mapRepository).saveMapState(captor.capture());

        MapState captured = captor.getValue();
        assertEquals("Latitude mismatch", expectedLat, captured.latitude, 0.0001);
        assertEquals("Longitude mismatch", expectedLng, captured.longitude, 0.0001);
        assertEquals("Zoom mismatch", expectedZoom, captured.zoomLevel, 0.0001);
    }

    // addToFavorites

    @Test
    public void addToFavorites_validPlace_callsRepositoryWithMappedData() {
        // Arrange
        Location loc = new Location(10.762, 106.682);
        Place place = new Place("id1", "Cafe ABC", "123 Nguyen Hue", 4.5, loc);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        Mockito.doAnswer(invocation -> {
            PersistenceCallback callback = invocation.getArgument(2);
            if (callback != null) {
                callback.onSuccess();
            }
            return null;
        }).when(placeRepository).persistPlace(eq(place), eq("favorite"), any(PersistenceCallback.class));

        // Act
        viewModel.addToFavorites(place, new MapViewModel.AddFavoriteCallback() {
            @Override
            public void onSuccess() {
                callbackCalled.set(true);
            }

            @Override
            public void onError(String message) {
                fail("Expected success but got error: " + message);
            }
        });

        // Assert
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        Mockito.verify(favoriteRepository).addFavorite(captor.capture());
        Favorite saved = captor.getValue();
        assertEquals("id1", saved.placeId);
        assertEquals("Cafe ABC", saved.name);
        assertEquals("123 Nguyen Hue", saved.address);
        assertEquals(10.762, saved.latitude, 0.001);
        assertEquals(106.682, saved.longitude, 0.001);
        assertEquals(4, saved.rating); // (int) 4.5 -> 4

        Mockito.verify(placeRepository).persistPlace(eq(place), eq("favorite"), any(PersistenceCallback.class));
        assertTrue(callbackCalled.get());
    }

    @Test
    public void addToFavorites_placeWithNullLocation_callsRepositoryWithZeroCoordinates() {
        // Arrange: place with no location
        Place place = new Place("id2", "No Loc Place", "789 Unknown St", 3.0, null);
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        Mockito.doAnswer(invocation -> {
            PersistenceCallback callback = invocation.getArgument(2);
            if (callback != null) {
                callback.onSuccess();
            }
            return null;
        }).when(placeRepository).persistPlace(eq(place), eq("favorite"), any(PersistenceCallback.class));

        // Act
        viewModel.addToFavorites(place, new MapViewModel.AddFavoriteCallback() {
            @Override
            public void onSuccess() {
                callbackCalled.set(true);
            }

            @Override
            public void onError(String message) {
                fail("Expected success but got error: " + message);
            }
        });

        // Assert: coordinates default to 0.0 when location is null
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        Mockito.verify(favoriteRepository).addFavorite(captor.capture());
        assertEquals(0.0, captor.getValue().latitude, 0.0001);
        assertEquals(0.0, captor.getValue().longitude, 0.0001);

        Mockito.verify(placeRepository).persistPlace(eq(place), eq("favorite"), any(PersistenceCallback.class));
        assertTrue(callbackCalled.get());
    }

    // removeFromFavorites

    @Test
    public void removeFromFavorites_validFavorite_callsRepositoryDeleteFavorite() {
        // Arrange
        Favorite fav = new Favorite();
        fav.id = "fav-10"; // Keep String id to match backend expectations
        fav.name = "BookCafe";
        fav.address = "District 3";

        // Act
        viewModel.removeFromFavorites(fav);

        // Assert
        Mockito.verify(favoriteRepository).deleteFavorite(fav);
    }

    // searchForPlaces

    @Test
    public void searchForPlaces_validQuery_callsPlaceRepository() {
        // Arrange
        String query = "university";
        Mockito.when(placeRepository.searchPlaces(query, null, true))
            .thenReturn(new MutableLiveData<>());
        viewModel.searchResults.observeForever(list -> { });

        // Act
        viewModel.searchForPlaces(query);

        // Assert
        Mockito.verify(placeRepository, timeout(1200))
            .searchPlaces(eq(query), isNull(), eq(true));
    }

    @Test
    public void searchForPlaces_rapidInput_dispatchesOnlyLatestDebouncedQuery() {
        android.os.Handler handler = Mockito.mock(android.os.Handler.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        MapViewModel vm = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                directExecutor,
                handler,
                400L);

        MutableLiveData<List<Place>> finalResults = new MutableLiveData<>(Collections.emptyList());
        when(placeRepository.searchPlaces("coffee near me", null, true)).thenReturn(finalResults);
        vm.searchResults.observeForever(list -> {
        });

        vm.searchForPlaces("coffee");
        vm.searchForPlaces("coffee near me");

        verify(handler, Mockito.atLeastOnce()).postDelayed(runnableCaptor.capture(), eq(400L));
        List<Runnable> postedRunnables = new ArrayList<>(runnableCaptor.getAllValues());
        assertTrue(postedRunnables.size() >= 2);

        Runnable firstRunnable = postedRunnables.get(0);
        Runnable latestRunnable = postedRunnables.get(postedRunnables.size() - 1);
        assertNotNull(firstRunnable);
        assertNotNull(latestRunnable);

        firstRunnable.run();

        verify(placeRepository, never()).searchPlaces(eq("coffee"), isNull(), eq(true));

        latestRunnable.run();

        verify(placeRepository).searchPlaces(eq("coffee near me"), isNull(), eq(true));
    }

    @Test
    public void searchForPlacesLive_validQuery_doesNotSaveToHistory() {
        String query = "coffee";
        Mockito.when(placeRepository.searchPlaces(query, null, false))
            .thenReturn(new MutableLiveData<>());
        viewModel.searchResults.observeForever(list -> {
        });

        viewModel.searchForPlacesLive(query);

        Mockito.verify(placeRepository, timeout(1200))
            .searchPlaces(eq(query), isNull(), eq(false));
    }

    @Test
    public void searchForPlaces_submitAfterLiveSearch_sameQueryStillSavesHistory() {
        android.os.Handler handler = Mockito.mock(android.os.Handler.class);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);

        MapViewModel vm = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                directExecutor,
                handler,
                400L);

        String query = "coffee";
        when(placeRepository.searchPlaces(query, null, false))
            .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        when(placeRepository.searchPlaces(query, null, true))
            .thenReturn(new MutableLiveData<>(Collections.emptyList()));
        vm.searchResults.observeForever(list -> {
        });

        vm.searchForPlacesLive(query);
        verify(handler, Mockito.atLeastOnce()).postDelayed(runnableCaptor.capture(), eq(400L));
        Runnable liveRunnable = runnableCaptor.getValue();
        assertNotNull(liveRunnable);
        liveRunnable.run();

        verify(placeRepository).searchPlaces(eq(query), isNull(), eq(false));

        vm.searchForPlaces(query);
        verify(handler, Mockito.atLeast(2)).postDelayed(runnableCaptor.capture(), eq(400L));
        List<Runnable> allRunnables = runnableCaptor.getAllValues();
        Runnable submitRunnable = allRunnables.get(allRunnables.size() - 1);
        assertNotNull(submitRunnable);
        submitRunnable.run();

        verify(placeRepository).searchPlaces(eq(query), isNull(), eq(true));
    }

    @Test
    public void searchForPlaces_emptyQuery_emitsEmptyImmediatelyWithoutRepositoryCall() {
        android.os.Handler handler = Mockito.mock(android.os.Handler.class);

        MapViewModel vm = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                directExecutor,
                handler,
                400L);

        vm.searchResults.observeForever(list -> {
        });

        vm.searchForPlaces("   ");

        List<Place> results = vm.searchResults.getValue();
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(placeRepository, never()).searchPlaces(anyString(), any(), Mockito.anyBoolean());
        verify(handler, never()).postDelayed(any(Runnable.class), anyLong());
    }

    // allFavorites

    @Test
    @SuppressWarnings("unchecked")
    public void allFavorites_whenRepositoryReturnsData_exposesDataThroughLiveData() {
        // Arrange: create a ViewModel with a properly stubbed favorites LiveData
        MutableLiveData<List<Favorite>> favsLiveData = new MutableLiveData<>();
        Mockito.when(favoriteRepository.getAllFavorites()).thenReturn(favsLiveData);
        Mockito.when(groupRepository.getGroups()).thenReturn(new MutableLiveData<>(Collections.emptyList()));
        MapViewModel vm = new MapViewModel(
            mapRepository,
            placeRepository,
            favoriteRepository,
            groupRepository,
            routeRepository,
            reviewRepository,
            directExecutor);

        Observer<List<Favorite>> observer = Mockito.mock(Observer.class);
        vm.allFavorites.observeForever(observer);

        Favorite fav = new Favorite();
        fav.name = "My Favorite Place";

        // Act
        favsLiveData.setValue(Collections.singletonList(fav));

        // Assert
        ArgumentCaptor<List<Favorite>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(observer).onChanged(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("My Favorite Place", captor.getValue().get(0).name);
    }

    @Test
    public void estimateRoute_whenRouteAvailable_updatesStatusTextWithSummary() {
        // Arrange
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        // Act
        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(new Route(2500.0, 600.0, "{}", "driving", Route.SOURCE_ONLINE));

        // Assert
        Event<String> event = viewModel.statusText.getValue();
        assertNotNull(event);
        String summary = event.peekContent();
        assertNotNull(summary);
        assertTrue(summary.contains("Route"));
        assertTrue(summary.toLowerCase().contains("online"));

        String routeSummary = viewModel.routeSummary.getValue();
        assertNotNull(routeSummary);
        assertTrue(routeSummary.contains("Route"));

        String routeGeometry = viewModel.routeGeometryJson.getValue();
        assertNotNull(routeGeometry);
        assertEquals("{}", routeGeometry);
    }

    @Test
    public void estimateRoute_whenRouteUnavailable_showsNoMapDataMessage() {
        // Arrange
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        // Act
        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(null);

        // Assert
        assertEquals("No map data downloaded", viewModel.routeSummary.getValue());
        assertEquals(null, viewModel.routeGeometryJson.getValue());

        Event<String> event = viewModel.statusText.getValue();
        assertNotNull(event);
        assertEquals("No map data downloaded", event.peekContent());
    }

    @Test
    public void beginRoutePreview_whenRouteAvailable_updatesRouteSession() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        Place destination = new Place("p1", "Science Museum", "1 Museum Rd", 4.6,
                new Location(10.1, 106.1));
        viewModel.beginRoutePreview(destination, new Location(10.0, 106.0), destination.location);

        routeLiveData.setValue(new Route(4200.0, 3900.0, "{\"type\":\"LineString\",\"coordinates\":[]}",
                "car", Route.SOURCE_BROUTER));

        RouteSession session = viewModel.getCurrentRouteSession();
        assertEquals(RouteSession.Status.READY, session.status);
        assertTrue(session.hasRoute());
        assertNotNull(session.destinationPlace);
        assertEquals("Science Museum", session.destinationPlace.name);
        assertEquals("1 hr 5 min", session.durationText);
        assertEquals("4.2 km", session.distanceText);
        assertEquals("{\"type\":\"LineString\",\"coordinates\":[]}", viewModel.routeGeometryJson.getValue());
    }

    @Test
    public void cancelRoute_clearsInMemoryRouteSessionAndGeometry() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(new Route(2500.0, 600.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.cancelRoute();

        assertEquals(RouteSession.Status.IDLE, viewModel.getCurrentRouteSession().status);
        assertNull(viewModel.routeSummary.getValue());
        assertNull(viewModel.routeGeometryJson.getValue());
    }

    @Test
    public void startFollowingRoute_andUpdateLocation_updatesRouteSessionNavigationState() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(new Route(2500.0, 600.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.startFollowingRoute();
        viewModel.updateFollowingLocation(new Location(10.02, 106.03), 450f);

        RouteSession session = viewModel.getCurrentRouteSession();
        assertTrue(session.following);
        assertNotNull(session.lastKnownLocation);
        assertEquals(10.02, session.lastKnownLocation.latitude, 0.0001);
        assertEquals(106.03, session.lastKnownLocation.longitude, 0.0001);
        assertEquals(90f, session.lastBearingDegrees, 0.0001f);
    }

    @Test
    public void updateFollowingLocation_whenRouteIsActiveButNotFollowing_stillUpdatesProgressState() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(new Route(2500.0, 600.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.updateFollowingLocation(new Location(10.05, 106.06), 180f);

        RouteSession session = viewModel.getCurrentRouteSession();
        assertFalse(session.following);
        assertNotNull(session.lastKnownLocation);
        assertEquals(10.05, session.lastKnownLocation.latitude, 0.0001);
        assertEquals(106.06, session.lastKnownLocation.longitude, 0.0001);
        assertEquals(180f, session.lastBearingDegrees, 0.0001f);
    }

    @Test
    public void updateFollowingLocation_whenLocationAndBearingAreEffectivelyUnchanged_keepsSessionInstance() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        viewModel.estimateRoute(new Location(10.0, 106.0), new Location(10.1, 106.1));
        routeLiveData.setValue(new Route(2500.0, 600.0, "{}", "driving", Route.SOURCE_ONLINE));
        viewModel.startFollowingRoute();

        viewModel.updateFollowingLocation(new Location(10.02, 106.03), 90f);
        RouteSession firstSession = viewModel.getCurrentRouteSession();

        viewModel.updateFollowingLocation(new Location(10.0200001, 106.0300001), 91f);
        RouteSession dedupedSession = viewModel.getCurrentRouteSession();

        assertSame(firstSession, dedupedSession);

        viewModel.updateFollowingLocation(new Location(10.022, 106.032), 91f);
        RouteSession updatedSession = viewModel.getCurrentRouteSession();

        assertNotSame(dedupedSession, updatedSession);
    }

    @Test
    public void updateFollowingLocation_whenFollowingAndArriving_dispatchesNavigationFinishedEvent() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        Place destination = new Place(
                "p1",
                "Ben Thanh",
                "Le Loi",
                4.2,
                new Location(10.7720, 106.6980));

        viewModel.beginRoutePreview(destination, new Location(10.7700, 106.6960), destination.location);
        routeLiveData.setValue(new Route(1800.0, 360.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.startFollowingRoute();
        viewModel.updateFollowingLocation(new Location(10.7720, 106.6980), 90f);

        Event<TripSummary> navigationEvent = viewModel.navigationFinishedEvent.getValue();
        assertNotNull(navigationEvent);
        TripSummary tripSummary = navigationEvent.getContentIfNotHandled();
        assertNotNull(tripSummary);
        assertTrue(tripSummary.getEndTime() >= tripSummary.getStartTime());
        assertTrue(tripSummary.getDurationFormatted().contains("phút"));
        assertTrue(tripSummary.getDurationFormatted().contains("giây"));
    }

    @Test
    public void updateFollowingLocation_whenAlreadyArrived_dispatchesNavigationFinishedEventOnlyOncePerSession() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        Place destination = new Place(
                "p1",
                "Ben Thanh",
                "Le Loi",
                4.2,
                new Location(10.7720, 106.6980));

        viewModel.beginRoutePreview(destination, new Location(10.7700, 106.6960), destination.location);
        routeLiveData.setValue(new Route(1800.0, 360.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.startFollowingRoute();
        viewModel.updateFollowingLocation(new Location(10.7720, 106.6980), 90f);

        Event<TripSummary> firstEvent = viewModel.navigationFinishedEvent.getValue();
        assertNotNull(firstEvent);

        viewModel.updateFollowingLocation(new Location(10.7720, 106.6980), 130f);

        Event<TripSummary> secondEvent = viewModel.navigationFinishedEvent.getValue();
        assertSame(firstEvent, secondEvent);
    }

    @Test
    public void updateFollowingLocation_whenNotFollowing_doesNotDispatchNavigationFinishedEvent() {
        MutableLiveData<Route> routeLiveData = new MutableLiveData<>();
        Mockito.when(routeRepository.getRoute(ArgumentMatchers.anyList())).thenReturn(routeLiveData);

        Place destination = new Place(
                "p1",
                "Ben Thanh",
                "Le Loi",
                4.2,
                new Location(10.7720, 106.6980));

        viewModel.beginRoutePreview(destination, new Location(10.7700, 106.6960), destination.location);
        routeLiveData.setValue(new Route(1800.0, 360.0, "{}", "driving", Route.SOURCE_ONLINE));

        viewModel.updateFollowingLocation(new Location(10.7720, 106.6980), 90f);

        assertNull(viewModel.navigationFinishedEvent.getValue());
    }

    @Test
    public void newViewModel_startsWithoutRouteSession() {
        MapViewModel freshViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
            reviewRepository,
            directExecutor);

        assertEquals(RouteSession.Status.IDLE, freshViewModel.getCurrentRouteSession().status);
        assertFalse(freshViewModel.hasActiveRouteSession());
        assertNull(freshViewModel.routeSummary.getValue());
        assertNull(freshViewModel.routeGeometryJson.getValue());
    }

    @Test
    public void loadReviews_WhenCalled_SetsLoadingStateAndTriggersResolution() throws InterruptedException {
        QueueExecutor queueExecutor = new QueueExecutor();
        MapViewModel queuedViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                queueExecutor);

        // Arrange
        Place place = new Place("ext-1", "Central Park", "NY", 4.8, new Location(40.78, -73.96));
        place.placeSource = "GOOGLE";
        
        when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
            .thenReturn("internal-123");

        // Act
        queuedViewModel.loadReviews(place);

        // Assert - loading stays true until queued background work runs
        assertTrue(queuedViewModel.isLoadingReviews.getValue());

        assertEquals(1, queueExecutor.tasks.size());
        queueExecutor.tasks.get(0).run();

        verify(reviewRepository).resolveInternalPlaceId("GOOGLE", "ext-1", 40.78, -73.96, "Central Park");
        verify(reviewRepository).refreshReviews(eq("internal-123"), ArgumentMatchers.any());
        assertFalse(queuedViewModel.isLoadingReviews.getValue());
    }

    @Test
    public void submitReview_WhenCalled_TriggersRepositoryAction() {
        // Arrange - set current place
        when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
            .thenReturn("internal-123");
        Place place = new Place("ext-1", "Park", "Loc", 4.0, new Location(0,0));
        place.placeSource = "OSM";
        viewModel.loadReviews(place);
        
        // Act
        viewModel.submitReview(5, "Excellent!");

        // Assert
        ArgumentCaptor<PlaceIdentityContext> contextCaptor = ArgumentCaptor.forClass(PlaceIdentityContext.class);
        verify(reviewRepository).submitReview(eq("internal-123"), eq(5), eq("Excellent!"), contextCaptor.capture());
        PlaceIdentityContext context = contextCaptor.getValue();
        assertNotNull(context);
        assertEquals("OSM", context.externalSource);
        assertEquals("ext-1", context.externalId);
        assertEquals(Double.valueOf(0.0), context.lat);
        assertEquals(Double.valueOf(0.0), context.lng);
        assertEquals("Park", context.placeName);
    }

    @Test
    public void submitReview_WhenReviewsAreLoading_IgnoresSubmission() {
        QueueExecutor queueExecutor = new QueueExecutor();
        MapViewModel queuedViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                queueExecutor);

        queuedViewModel.loadReviews(new Place("ext-1", "Park", "Loc", 4.0, new Location(0, 0)));
        assertTrue(queuedViewModel.isLoadingReviews.getValue());

        queuedViewModel.submitReview(5, "Should be ignored");

        verify(reviewRepository, never()).submitReview(anyString(), anyInt(), anyString(), any(PlaceIdentityContext.class));
    }

    @Test
    public void updateReview_WhenCalled_TriggersRepositoryUpdate() {
        when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
                .thenReturn("internal-123");
        Place place = new Place("ext-1", "Park", "Loc", 4.0, new Location(0,0));
        place.placeSource = "OSM";
        viewModel.loadReviews(place);

        Review existing = new Review();
        existing.placeId = "internal-123";
        existing.userId = "u1";

        viewModel.updateReview(existing, 4, "Updated");

        ArgumentCaptor<PlaceIdentityContext> contextCaptor = ArgumentCaptor.forClass(PlaceIdentityContext.class);
        verify(reviewRepository).updateReview(eq("internal-123"), eq(4), eq("Updated"), contextCaptor.capture());
        PlaceIdentityContext context = contextCaptor.getValue();
        assertNotNull(context);
        assertEquals("OSM", context.externalSource);
        assertEquals("ext-1", context.externalId);
        assertEquals(Double.valueOf(0.0), context.lat);
        assertEquals(Double.valueOf(0.0), context.lng);
        assertEquals("Park", context.placeName);
    }

    @Test
    public void loadReviews_WhenCalledTwice_OnlyLatestRequestUpdatesAndRefreshes() {
        QueueExecutor queueExecutor = new QueueExecutor();
        MapViewModel queuedViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                queueExecutor);

        Mockito.when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
                .thenReturn("internal-old")
                .thenReturn("internal-new");
        Mockito.doAnswer(invocation -> {
            Runnable callback = invocation.getArgument(1);
            if (callback != null) {
                callback.run();
            }
            return null;
        }).when(reviewRepository).refreshReviews(any(), any());

        Place oldPlace = new Place("ext-old", "Old", "A", 4.0, new Location(1, 1));
        oldPlace.placeSource = "OSM";
        Place newPlace = new Place("ext-new", "New", "B", 4.0, new Location(2, 2));
        newPlace.placeSource = "OSM";

        queuedViewModel.loadReviews(oldPlace);
        queuedViewModel.loadReviews(newPlace);

        assertEquals(2, queueExecutor.tasks.size());

        queueExecutor.tasks.get(0).run();
        queueExecutor.tasks.get(1).run();

        verify(reviewRepository, never()).refreshReviews(eq("internal-old"), any());
        verify(reviewRepository).refreshReviews(eq("internal-new"), any());
        assertFalse(queuedViewModel.isLoadingReviews.getValue());
    }
}
