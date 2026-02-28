package com.bif.app.feature.map;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.data.repository.LocationRepository;
import com.bif.app.data.repository.MapRepository;
import com.bif.app.domain.model.Location;
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
    private MapRepository mapRepository;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // Clear any existing map state
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();

        LocationRepository locationRepository = new LocationRepository(context);
        mapRepository = new MapRepository(context);
        viewModel = new MapViewModel(locationRepository, mapRepository);
    }

    @After
    public void tearDown() {
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
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
                new LocationRepository(context),
                mapRepository
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

        // Wait for geocoding to complete
        Thread.sleep(3000);

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