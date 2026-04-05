package com.bif.app.feature.map;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.LiveData;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IRouteRepository;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.Group;
import java.util.List;
import java.util.Collections;
import com.bif.app.domain.model.MapState;

import org.junit.Before;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test for MapViewModel with real repositories.
 */
@RunWith(AndroidJUnit4.class)
public class MapViewModelInstrumentedTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private MapViewModel viewModel;
    private IMapRepository mapRepository;
    private IPlaceRepository placeRepository;
    private IFavoriteRepository favoriteRepository;
    private IGroupRepository groupRepository;
    private IRouteRepository routeRepository;
    private Context context;

    private static class FakeMapRepository implements IMapRepository {
        private MapState state;
        @Override
        public void saveMapState(MapState state) { this.state = state; }
        @Override
        public MapState getMapState() { return state; }
    }

    private static class FakePlaceRepository implements IPlaceRepository {
        @Override
        public LiveData<Location> searchLocation(String query) {
            MutableLiveData<Location> result = new MutableLiveData<>();
            if ("Ho Chi Minh City University of Science".equals(query)) {
                result.postValue(new Location(10.762, 106.682));
            } else {
                result.postValue(null);
            }
            return result;
        }

        @Override
        public LiveData<List<Place>> searchPlaces(String query, Location userLocation) {
            return new MutableLiveData<>(Collections.emptyList());
        }

        @Override
        public LiveData<List<Place>> searchPlacesFromHistory(String query) {
            return new MutableLiveData<>(Collections.emptyList());
        }

        @Override
        public void persistPlace(Place place, String action) {
        }

        @Override
        public LiveData<List<Place>> getAllPersistedPlaces() {
            return new MutableLiveData<>(Collections.emptyList());
        }

        @Override
        public LiveData<List<String>> getSearchHistory() {
            return new MutableLiveData<>(Collections.emptyList());
        }
    }

    private static class FakeFavoriteRepository implements IFavoriteRepository {
        @Override
        public LiveData<List<Favorite>> getAllFavorites() { return new MutableLiveData<>(Collections.emptyList()); }
        @Override
        public LiveData<List<Favorite>> searchFavorites(String query) { return new MutableLiveData<>(Collections.emptyList()); }
        @Override
        public void addFavorite(Favorite favorite) {}
        @Override
        public void updateFavorite(Favorite favorite) {}
        @Override
        public void updateAllFavorites(List<Favorite> favorites) {}
        @Override
        public void deleteFavorite(Favorite favorite) {}
        @Override
        public void refreshFavorites(SyncCallback callback) {
            if (callback != null) {
                callback.onSuccess();
            }
        }
    }

    private static class FakeGroupRepository implements IGroupRepository {
        @Override
        public LiveData<List<Group>> getGroups() {
            return new MutableLiveData<>(Collections.emptyList());
        }

        @Override
        public LiveData<Group> getGroupById(int groupId) {
            return new MutableLiveData<>(null);
        }

        @Override
        public void createGroup(String name, List<Friend> selectedFriends) {
        }

        @Override
        public void updateGroup(Group group) {
        }

        @Override
        public void addMember(int groupId, int friendId) {
        }

        @Override
        public void removeMember(int groupId, int friendId) {
        }

        @Override
        public void updateMemberRole(int groupId, int friendId, String role) {
        }

        @Override
        public void leaveGroup(Group group) {
        }

        @Override
        public void disbandGroup(Group group) {
        }

        @Override
        public void refreshGroups() {
        }
    }

    private static class FakeRouteRepository implements IRouteRepository {
        @Override
        public LiveData<com.bif.app.domain.model.Route> getRoute(List<Location> waypoints) {
            return new MutableLiveData<>(null);
        }
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        placeRepository = new FakePlaceRepository();
        mapRepository = new FakeMapRepository();
        favoriteRepository = new FakeFavoriteRepository();
        groupRepository = new FakeGroupRepository();
        routeRepository = new FakeRouteRepository();
        viewModel = new MapViewModel(
            mapRepository,
            placeRepository,
            favoriteRepository,
            groupRepository,
            routeRepository);
    }

    @After
    public void tearDown() {
        // Nothing to clean up for fakes
    }

    @Test
    public void saveAndGetMapState_persistsAcrossViewModelLifecycle() {
        // Arrange
        double expectedLat = 10.7626636;
        double expectedLng = 106.6823091;
        float expectedZoom = 15.5f;

        // Act - Save state
        viewModel.saveMapState(expectedLat, expectedLng, expectedZoom);

        // Create new ViewModel instance (simulating configuration change)
        MapViewModel newViewModel = new MapViewModel(
                mapRepository,
                placeRepository,
            favoriteRepository,
            groupRepository,
            routeRepository
        );

        MapState retrievedState = newViewModel.getLastMapState();

        // Assert
        assertNotNull("State should persist across ViewModel instances", retrievedState);
        assertEquals("Latitude should persist", expectedLat, retrievedState.latitude, 0.0001);
        assertEquals("Longitude should persist", expectedLng, retrievedState.longitude, 0.0001);
        assertEquals("Zoom should persist", expectedZoom, retrievedState.zoomLevel, 0.0001f);
    }

    @Test
    public void setStatusText_updatesLiveData() throws InterruptedException {
        // Arrange
        final String[] observedValue = new String[1];
        Observer<Event<String>> observer = value -> {
            if (value != null) {
                observedValue[0] = value.peekContent();
            }
        };
        viewModel.statusText.observeForever(observer);

        // Act
        viewModel.setStatusText("Test Status");

        // Give LiveData time to update
        Thread.sleep(100);

        // Assert
        assertEquals("Status text should update", "Test Status", observedValue[0]);

        // Cleanup
        viewModel.statusText.removeObserver(observer);
    }

    @Test
    public void searchLocation_hcmus_returnsValidResult() throws InterruptedException {
        // Arrange
        final Location[] observedLocation = new Location[1];
        Observer<Location> observer = location -> observedLocation[0] = location;
        viewModel.searchResult.observeForever(observer);

        // Act
        viewModel.searchLocation("Ho Chi Minh City University of Science");

        // Wait for LiveData postValue to process
        Thread.sleep(500);

        // Assert
        assertNotNull("Location should be found", observedLocation[0]);
        assertTrue("Latitude should be near HCMUS",
                Math.abs(observedLocation[0].latitude - 10.76) < 0.1);
        assertTrue("Longitude should be near HCMUS",
                Math.abs(observedLocation[0].longitude - 106.68) < 0.1);

        // Cleanup
        viewModel.searchResult.removeObserver(observer);
    }

    @Test
    public void getLastMapState_noDataSaved_returnsNull() {
        // Act
        MapState result = viewModel.getLastMapState();

        // Assert
        assertNull("Should return null when no state is saved", result);
    }
}
