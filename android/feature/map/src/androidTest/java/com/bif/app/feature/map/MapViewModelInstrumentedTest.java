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
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
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
        public LiveData<List<Place>> searchPlaces(String query) {
            return new MutableLiveData<>(Collections.emptyList());
        }
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        placeRepository = new FakePlaceRepository();
        mapRepository = new FakeMapRepository();
        viewModel = new MapViewModel(mapRepository, placeRepository);
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
                placeRepository
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
        Observer<String> observer = value -> observedValue[0] = value;
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