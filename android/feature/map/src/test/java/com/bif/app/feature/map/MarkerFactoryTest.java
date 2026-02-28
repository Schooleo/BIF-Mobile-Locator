package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class MarkerFactoryTest {

    private MockedStatic<BitmapDescriptorFactory> mockedBitmapDescriptorFactory;
    private BitmapDescriptor mockBitmapDescriptor;

    @Before
    public void setUp() {
        mockedBitmapDescriptorFactory = mockStatic(BitmapDescriptorFactory.class);
        mockBitmapDescriptor = mock(BitmapDescriptor.class);
        
        mockedBitmapDescriptorFactory.when(() -> BitmapDescriptorFactory.defaultMarker(anyFloat()))
                .thenReturn(mockBitmapDescriptor);
        mockedBitmapDescriptorFactory.when(() -> BitmapDescriptorFactory.defaultMarker())
                .thenReturn(mockBitmapDescriptor);
    }

    @After
    public void tearDown() {
        mockedBitmapDescriptorFactory.close();
    }

    @Test(expected = IllegalArgumentException.class)
    public void createMarker_nullPosition_throwsException() {
        MarkerFactory.createMarker(null);
    }

    @Test
    public void createMarker_validPosition_returnsMarkerOptions() {
        // Arrange
        LatLng position = new LatLng(10.0, 20.0);
        String title = "Test Title";
        String snippet = "Test Snippet";

        // Act
        MarkerOptions result = MarkerFactory.createMarker(position, title, snippet);

        // Assert
        assertNotNull(result);
        assertEquals(position, result.getPosition());
        assertEquals(title, result.getTitle());
        assertEquals(snippet, result.getSnippet());
        assertEquals(mockBitmapDescriptor, result.getIcon());
    }

    @Test
    public void createMarker_defaultValues_returnsMarkerOptions() {
        // Arrange
        LatLng position = new LatLng(10.0, 20.0);

        // Act
        MarkerOptions result = MarkerFactory.createMarker(position);

        // Assert
        assertNotNull(result);
        assertEquals(position, result.getPosition());
        assertEquals("Location", result.getTitle());
        assertEquals("Selected Marker Location", result.getSnippet());
        assertEquals(mockBitmapDescriptor, result.getIcon());
    }

    @Test
    public void createCurrentLocationMarker_returnsBlueMarker() {
        // Arrange
        LatLng position = new LatLng(10.0, 20.0);

        // Act
        MarkerOptions result = MarkerFactory.createCurrentLocationMarker(position);

        // Assert
        assertNotNull(result);
        assertEquals(position, result.getPosition());
        assertEquals("Your Location", result.getTitle());
        assertEquals("You are here", result.getSnippet());
        assertEquals(mockBitmapDescriptor, result.getIcon());
    }

    @Test
    public void createPlaceMarker_returnsGreenMarker() {
        // Arrange
        LatLng position = new LatLng(10.0, 20.0);
        String title = "HCMUS";

        // Act
        MarkerOptions result = MarkerFactory.createPlaceMarker(position, title);

        // Assert
        assertNotNull(result);
        assertEquals(position, result.getPosition());
        assertEquals(title, result.getTitle());
        assertEquals(mockBitmapDescriptor, result.getIcon());
    }
}
