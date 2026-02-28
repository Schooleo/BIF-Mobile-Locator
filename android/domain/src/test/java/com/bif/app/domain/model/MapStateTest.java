package com.bif.app.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class MapStateTest {

    @Test
    public void constructor_validInput_setsFieldsCorrectly() {
        // Arrange
        double lat = 10.7626636;
        double lng = 106.6823091;
        float zoom = 15.5f;

        // Act
        MapState state = new MapState(lat, lng, zoom);

        // Assert
        assertEquals("Latitude should match", lat, state.latitude, 0.0001);
        assertEquals("Longitude should match", lng, state.longitude, 0.0001);
        assertEquals("Zoom should match", zoom, state.zoomLevel, 0.0001f);
    }

    @Test
    public void equals_sameValues_returnsTrue() {
        // Arrange
        MapState state1 = new MapState(10.0, 20.0, 15.0f);
        MapState state2 = new MapState(10.0, 20.0, 15.0f);

        // Act & Assert
        Assert.assertEquals("States with same values should be equal", state1, state2);
    }

    @Test
    public void equals_differentLatitude_returnsFalse() {
        // Arrange
        MapState state1 = new MapState(10.0, 20.0, 15.0f);
        MapState state2 = new MapState(10.1, 20.0, 15.0f);

        // Act & Assert
        Assert.assertNotEquals("States with different latitude should not be equal", state1, state2);
    }

    @Test
    public void equals_differentLongitude_returnsFalse() {
        // Arrange
        MapState state1 = new MapState(10.0, 20.0, 15.0f);
        MapState state2 = new MapState(10.0, 20.1, 15.0f);

        // Act & Assert
        Assert.assertNotEquals("States with different longitude should not be equal", state1, state2);
    }

    @Test
    public void equals_differentZoom_returnsFalse() {
        // Arrange
        MapState state1 = new MapState(10.0, 20.0, 15.0f);
        MapState state2 = new MapState(10.0, 20.0, 16.0f);

        // Act & Assert
        Assert.assertNotEquals("States with different zoom should not be equal", state1, state2);
    }

    @Test
    public void equals_sameObject_returnsTrue() {
        // Arrange
        MapState state = new MapState(10.0, 20.0, 15.0f);

        // Act & Assert
        Assert.assertEquals("State should equal itself", state, state);
    }

    @Test
    public void equals_null_returnsFalse() {
        // Arrange
        MapState state = new MapState(10.0, 20.0, 15.0f);

        // Act & Assert
        Assert.assertNotEquals("State should not equal null", null, state);
    }

    @Test
    public void hashCode_sameValues_returnsSameHash() {
        // Arrange
        MapState state1 = new MapState(10.0, 20.0, 15.0f);
        MapState state2 = new MapState(10.0, 20.0, 15.0f);

        // Act & Assert
        assertEquals("Equal states should have same hashCode", state1.hashCode(), state2.hashCode());
    }

    @Test
    public void constructor_extremeValues_handlesCorrectly() {
        // Arrange & Act
        MapState maxState = new MapState(90.0, 180.0, 21.0f);
        MapState minState = new MapState(-90.0, -180.0, 1.0f);

        // Assert
        assertEquals("Max latitude", 90.0, maxState.latitude, 0.0001);
        assertEquals("Min latitude", -90.0, minState.latitude, 0.0001);
        assertEquals("Max longitude", 180.0, maxState.longitude, 0.0001);
        assertEquals("Min longitude", -180.0, minState.longitude, 0.0001);
    }

    @Test
    public void constructor_zeroValues_handlesCorrectly() {
        // Arrange & Act
        MapState state = new MapState(0.0, 0.0, 0.0f);

        // Assert
        assertEquals("Zero latitude", 0.0, state.latitude, 0.0001);
        assertEquals("Zero longitude", 0.0, state.longitude, 0.0001);
        assertEquals("Zero zoom", 0.0f, state.zoomLevel, 0.0001f);
    }
}