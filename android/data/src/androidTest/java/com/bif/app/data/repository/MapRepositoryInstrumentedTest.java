package com.bif.app.data.repository;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.domain.model.MapState;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MapRepositoryInstrumentedTest {

    private MapRepository repository;
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        repository = new MapRepository(context);

        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @After
    public void tearDown() {
        context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void saveAndRetrieveMapState_persistsCorrectly() {
        // Arrange
        MapState originalState = new MapState(10.7626636, 106.6823091, 15.5f);

        // Act
        repository.saveMapState(originalState);
        MapState retrievedState = repository.getMapState();

        // Assert
        assertNotNull("Retrieved state should not be null", retrievedState);
        assertEquals("Latitude should persist", originalState.latitude, retrievedState.latitude, 0.0);
        assertEquals("Longitude should persist", originalState.longitude, retrievedState.longitude, 0.0);
        assertEquals("Zoom should persist", originalState.zoomLevel, retrievedState.zoomLevel, 0.0001f);
    }

    @Test
    public void getMapState_noDataSaved_returnsNull() {
        // Act
        MapState result = repository.getMapState();

        // Assert
        assertNull("Should return null when no data exists", result);
    }

    @Test
    public void saveMapState_overwritesPreviousData() {
        // Arrange
        MapState firstState = new MapState(10.0, 20.0, 10.0f);
        MapState secondState = new MapState(15.0, 25.0, 12.0f);

        // Act
        repository.saveMapState(firstState);
        repository.saveMapState(secondState);
        MapState retrievedState = repository.getMapState();

        // Assert
        assertNotNull("Retrieved state should not be null", retrievedState);
        assertEquals("Should retrieve latest latitude", secondState.latitude, retrievedState.latitude, 0.0);
        assertEquals("Should retrieve latest longitude", secondState.longitude, retrievedState.longitude, 0.0);
        assertEquals("Zoom should match", secondState.zoomLevel, retrievedState.zoomLevel, 0.0001f);
    }

    @Test
    public void saveMapState_negativeCoordinates_persistsCorrectly() {
        // Arrange
        MapState state = new MapState(-45.5, -90.5, 8.0f);

        // Act
        repository.saveMapState(state);
        MapState retrievedState = repository.getMapState();

        // Assert
        assertNotNull("Retrieved state should not be null", retrievedState);
        assertEquals("Negative latitude should persist", state.latitude, retrievedState.latitude, 0.0);
        assertEquals("Negative longitude should persist", state.longitude, retrievedState.longitude, 0.0);
    }

    @Test
    public void saveMapState_extremeZoomLevels_persistsCorrectly() {
        // Arrange
        MapState minZoomState = new MapState(10.0, 20.0, 1.0f);
        MapState maxZoomState = new MapState(10.0, 20.0, 21.0f);

        // Act & Assert - Min Zoom
        repository.saveMapState(minZoomState);
        MapState retrievedMin = repository.getMapState();
        assertNotNull("Min zoom state should not be null", retrievedMin);
        assertEquals("Min zoom should persist", 1.0f, retrievedMin.zoomLevel, 0.0001f);

        // Act & Assert - Max Zoom
        repository.saveMapState(maxZoomState);
        MapState retrievedMax = repository.getMapState();
        assertNotNull("Max zoom state should not be null", retrievedMax);
        assertEquals("Max zoom should persist", 21.0f, retrievedMax.zoomLevel, 0.0001f);
    }

    @Test
    public void saveMapState_hcmusCoordinates_persistsAccurately() {
        // Arrange - Real HCMUS coordinates with full double precision
        double expectedLat = 10.7626636;
        double expectedLng = 106.6823091;
        MapState hcmusState = new MapState(expectedLat, expectedLng, 15.0f);

        // Act
        repository.saveMapState(hcmusState);
        MapState retrievedState = repository.getMapState();

        // Assert - Now with zero tolerance for double precision
        assertNotNull("HCMUS state should not be null", retrievedState);
        assertEquals("HCMUS latitude should persist with full precision",
                expectedLat, retrievedState.latitude, 0.0);
        assertEquals("HCMUS longitude should persist with full precision",
                expectedLng, retrievedState.longitude, 0.0);
    }

    @Test
    public void multipleInstances_shareData() {
        // Arrange
        MapRepository repo1 = new MapRepository(context);
        MapRepository repo2 = new MapRepository(context);
        MapState state = new MapState(10.0, 20.0, 15.0f);

        // Act
        repo1.saveMapState(state);
        MapState retrievedState = repo2.getMapState();

        // Assert
        assertNotNull("Second repository should retrieve data from first", retrievedState);
        assertEquals("Data should be shared across instances",
                state.latitude, retrievedState.latitude, 0.0);
    }
}