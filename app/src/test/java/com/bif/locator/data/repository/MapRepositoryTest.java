package com.bif.locator.data.repository;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.SharedPreferences;

import com.bif.locator.domain.model.MapState;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class MapRepositoryTest {

    @Mock Context context;
    @Mock SharedPreferences sharedPreferences;
    @Mock SharedPreferences.Editor editor;

    private MapRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences);
        when(sharedPreferences.edit()).thenReturn(editor);
        when(editor.putLong(anyString(), anyLong())).thenReturn(editor);
        when(editor.putFloat(anyString(), anyFloat())).thenReturn(editor);

        repository = new MapRepository(context);
    }

    @Test
    public void getMapState_whenNoData_returnsNull() {
        // Arrange
        when(sharedPreferences.contains("lat")).thenReturn(false);

        // Act
        MapState result = repository.getMapState();

        // Assert
        assertNull("Should return null when no data exists", result);
    }

    @Test
    public void saveMapState_callsSharedPreferences() {
        // Arrange
        MapState state = new MapState(10.0, 20.0, 15f);

        // Act
        repository.saveMapState(state);

        // Assert
        verify(editor).putLong(eq("lat"), eq(Double.doubleToRawLongBits(10.0)));
        verify(editor).putLong(eq("lng"), eq(Double.doubleToRawLongBits(20.0)));
        verify(editor).putFloat("zoom", 15.0f);
        verify(editor).apply();
    }

    @Test
    public void getMapState_withSavedData_returnsCorrectState() {
        // Arrange
        double lat = 10.5;
        double lng = 20.5;
        float zoom = 12.5f;

        when(sharedPreferences.contains("lat")).thenReturn(true);
        when(sharedPreferences.getLong("lat", 0)).thenReturn(Double.doubleToRawLongBits(lat));
        when(sharedPreferences.getLong("lng", 0)).thenReturn(Double.doubleToRawLongBits(lng));
        when(sharedPreferences.getFloat("zoom", 15f)).thenReturn(zoom);

        // Act
        MapState result = repository.getMapState();

        // Assert
        assertNotNull("Should return a state", result);
        assertEquals("Latitude should match", lat, result.latitude, 0.000001);
        assertEquals("Longitude should match", lng, result.longitude, 0.000001);
        assertEquals("Zoom should match", zoom, result.zoomLevel, 0.001f);
    }

    @Test
    public void saveMapState_negativeCoordinates_savesCorrectly() {
        // Arrange
        MapState state = new MapState(-45.0, -90.0, 10f);

        // Act
        repository.saveMapState(state);

        // Assert
        verify(editor).putLong(eq("lat"), eq(Double.doubleToRawLongBits(-45.0)));
        verify(editor).putLong(eq("lng"), eq(Double.doubleToRawLongBits(-90.0)));
        verify(editor).putFloat("zoom", 10.0f);
    }

    @Test
    public void saveMapState_extremeZoomLevels_savesCorrectly() {
        // Arrange
        MapState minZoom = new MapState(10.0, 20.0, 1.0f);
        MapState maxZoom = new MapState(10.0, 20.0, 21.0f);

        // Act
        repository.saveMapState(minZoom);
        repository.saveMapState(maxZoom);

        // Assert
        verify(editor, times(2)).putLong(eq("lat"), anyLong());
        verify(editor).putFloat("zoom", 1.0f);
        verify(editor).putFloat("zoom", 21.0f);
    }

    @Test
    public void constructor_verifiesSharedPreferencesName() {
        // Act & Assert
        verify(context).getSharedPreferences("map_prefs", Context.MODE_PRIVATE);
    }

    @Test
    public void saveAndGet_roundTrip_maintainsPrecision() {
        // Arrange
        double preciseLat = 10.7626636;
        double preciseLng = 106.6823091;
        MapState originalState = new MapState(preciseLat, preciseLng, 15.5f);

        repository.saveMapState(originalState);

        // Mock the retrieval with exact double precision
        when(sharedPreferences.contains("lat")).thenReturn(true);
        when(sharedPreferences.getLong("lat", 0))
                .thenReturn(Double.doubleToRawLongBits(preciseLat));
        when(sharedPreferences.getLong("lng", 0))
                .thenReturn(Double.doubleToRawLongBits(preciseLng));
        when(sharedPreferences.getFloat("zoom", 15f)).thenReturn(15.5f);

        // Act
        MapState retrievedState = repository.getMapState();

        // Assert
        assertNotNull("Retrieved state should not be null", retrievedState);
        assertEquals("Latitude should be preserved with full precision",
                preciseLat, retrievedState.latitude, 0.0);
        assertEquals("Longitude should be preserved with full precision",
                preciseLng, retrievedState.longitude, 0.0);
        assertEquals("Zoom should be preserved",
                15.5f, retrievedState.zoomLevel, 0.001f);
    }

    @Test
    public void saveMapState_hcmusCoordinates_maintainsPrecision() {
        // Arrange - Exact HCMUS coordinates
        MapState hcmusState = new MapState(10.7626636, 106.6823091, 15.0f);

        // Act
        repository.saveMapState(hcmusState);

        // Assert - Verify exact double bits are stored
        verify(editor).putLong(eq("lat"), eq(Double.doubleToRawLongBits(10.7626636)));
        verify(editor).putLong(eq("lng"), eq(Double.doubleToRawLongBits(106.6823091)));
    }
}