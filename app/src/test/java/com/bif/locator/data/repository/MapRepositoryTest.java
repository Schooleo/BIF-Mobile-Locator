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
        when(editor.putFloat(anyString(), anyFloat())).thenReturn(editor);

        repository = new MapRepository(context);
    }

    @Test
    public void getMapState_whenNoData_returnsNull() {
        // Arrange: SharedPreferences does not contain the key
        when(sharedPreferences.contains("lat")).thenReturn(false);

        // Act
        MapState result = repository.getMapState();

        // Assert
        assertNull(result);
    }

    @Test
    public void saveMapState_callsSharedPreferences() {
        // Arrange
        MapState state = new MapState(10.0, 20.0, 15f);

        // Act
        repository.saveMapState(state);

        // Assert
        verify(editor).putFloat("lat", 10.0f);
        verify(editor).putFloat("lng", 20.0f);
        verify(editor).putFloat("zoom", 15.0f);
        verify(editor).apply();
    }
}