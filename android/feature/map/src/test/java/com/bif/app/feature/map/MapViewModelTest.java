package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IReviewRepository;
import com.bif.app.domain.repository.IRouteRepository;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

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

    private MapViewModel viewModel;

    @Before
    public void setUp() {
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

        viewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository);
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

        // Act
        viewModel.addToFavorites(place);

        // Assert
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        Mockito.verify(favoriteRepository).addFavorite(captor.capture());
        Favorite saved = captor.getValue();
        assertEquals("Cafe ABC", saved.name);
        assertEquals("123 Nguyen Hue", saved.address);
        assertEquals(10.762, saved.latitude, 0.001);
        assertEquals(106.682, saved.longitude, 0.001);
        assertEquals(4, saved.rating); // (int) 4.5 -> 4

        // Also verifies the place is persisted locally (new behavior)
        Mockito.verify(placeRepository).persistPlace(place, "favorite");
    }

    @Test
    public void addToFavorites_placeWithNullLocation_callsRepositoryWithZeroCoordinates() {
        // Arrange: place with no location
        Place place = new Place("id2", "No Loc Place", "789 Unknown St", 3.0, null);

        // Act
        viewModel.addToFavorites(place);

        // Assert: coordinates default to 0.0 when location is null
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        Mockito.verify(favoriteRepository).addFavorite(captor.capture());
        assertEquals(0.0, captor.getValue().latitude, 0.0001);
        assertEquals(0.0, captor.getValue().longitude, 0.0001);

        // Also verifies the place is persisted locally even with null location
        Mockito.verify(placeRepository).persistPlace(place, "favorite");
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
        Mockito.when(placeRepository.searchPlaces(query, null)).thenReturn(new MutableLiveData<>());
        viewModel.searchResults.observeForever(list -> { });

        // Act
        viewModel.searchForPlaces(query);

        // Assert
        Mockito.verify(placeRepository, timeout(1200)).searchPlaces(eq(query), isNull());
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
            reviewRepository);

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
    public void newViewModel_startsWithoutRouteSession() {
        MapViewModel freshViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository);

        assertEquals(RouteSession.Status.IDLE, freshViewModel.getCurrentRouteSession().status);
        assertFalse(freshViewModel.hasActiveRouteSession());
        assertNull(freshViewModel.routeSummary.getValue());
        assertNull(freshViewModel.routeGeometryJson.getValue());
    }

    @Test
    public void loadReviews_WhenCalled_SetsLoadingStateAndTriggersResolution() throws InterruptedException {
        // Arrange
        Place place = new Place("ext-1", "Central Park", "NY", 4.8, new Location(40.78, -73.96));
        place.placeSource = "GOOGLE";
        
        when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
            .thenReturn("internal-123");

        // Act
        viewModel.loadReviews(place);

        // Assert - immediate loading state
        assertTrue(viewModel.isLoadingReviews.getValue());
        
        // Wait for thread completion (resolution is in a new Thread)
        Thread.sleep(100); 

        verify(reviewRepository).resolveInternalPlaceId("GOOGLE", "ext-1", 40.78, -73.96, "Central Park");
        verify(reviewRepository).refreshReviews("internal-123");
        assertFalse(viewModel.isLoadingReviews.getValue());
    }

    @Test
    public void submitReview_WhenCalled_TriggersRepositoryAction() {
        // Arrange - set current place
        when(reviewRepository.resolveInternalPlaceId(any(), any(), anyDouble(), anyDouble(), any()))
            .thenReturn("internal-123");
        viewModel.loadReviews(new Place("ext-1", "Park", "Loc", 4.0, new Location(0,0)));
        
        // Wait for thread completion (resolution is in a new Thread)
        try { Thread.sleep(100); } catch (InterruptedException e) {} 
        
        // Act
        viewModel.submitReview(5, "Excellent!");

        // Assert
        verify(reviewRepository).submitReview("internal-123", 5, "Excellent!");
    }
}
