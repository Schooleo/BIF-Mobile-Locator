package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;

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

        viewModel = new MapViewModel(mapRepository, placeRepository, favoriteRepository, groupRepository);
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

        // Assert — statusText is now LiveData<Event<String>>
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

    // ─── addToFavorites ──────────────────────────────────────────────────────

    @Test
    public void addToFavorites_validPlace_callsRepositoryWithMappedData() {
        // Arrange
        Location loc = new Location(10.762, 106.682);
        Place place = new Place("id1", "Café ABC", "123 Nguyen Hue", 4.5, loc);

        // Act
        viewModel.addToFavorites(place);

        // Assert
        ArgumentCaptor<Favorite> captor = ArgumentCaptor.forClass(Favorite.class);
        Mockito.verify(favoriteRepository).addFavorite(captor.capture());
        Favorite saved = captor.getValue();
        assertEquals("Café ABC", saved.name);
        assertEquals("123 Nguyen Hue", saved.address);
        assertEquals(10.762, saved.latitude, 0.001);
        assertEquals(106.682, saved.longitude, 0.001);
        assertEquals(4, saved.rating); // (int) 4.5 → 4

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

    // ─── removeFromFavorites ─────────────────────────────────────────────────

    @Test
    public void removeFromFavorites_validFavorite_callsRepositoryDeleteFavorite() {
        // Arrange
        Favorite fav = new Favorite();
        fav.id = "fav-10"; // Giữ String từ nhánh của bạn để khớp với Backend
        fav.name = "BookCafe";
        fav.address = "District 3";

        // Act
        viewModel.removeFromFavorites(fav);

        // Assert
        Mockito.verify(favoriteRepository).deleteFavorite(fav);
    }

    // ─── searchForPlaces ─────────────────────────────────────────────────────

    @Test
    public void searchForPlaces_validQuery_callsPlaceRepository() {
        // Arrange
        String query = "university";
        Mockito.when(placeRepository.searchPlaces(query)).thenReturn(new MutableLiveData<>());
        viewModel.searchResults.observeForever(list -> { });

        // Act
        viewModel.searchForPlaces(query);

        // Assert
        Mockito.verify(placeRepository).searchPlaces(query);
    }

    // ─── allFavorites ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    public void allFavorites_whenRepositoryReturnsData_exposesDataThroughLiveData() {
        // Arrange: create a ViewModel with a properly stubbed favorites LiveData
        MutableLiveData<List<Favorite>> favsLiveData = new MutableLiveData<>();
        Mockito.when(favoriteRepository.getAllFavorites()).thenReturn(favsLiveData);
        Mockito.when(groupRepository.getGroups()).thenReturn(new MutableLiveData<>(Collections.emptyList()));
        MapViewModel vm = new MapViewModel(mapRepository, placeRepository, favoriteRepository, groupRepository);

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
}