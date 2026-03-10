package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.location.Address;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlaceRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private GoogleMapsDataSource mockGoogleMapsDataSource;

    private PlaceRepository placeRepository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        placeRepository = new PlaceRepository(mockGoogleMapsDataSource);
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    // --- searchLocation Tests ---

    @Test
    public void searchLocation_validQuery_successReturnsLocation() throws IOException, InterruptedException {
        // Arrange
        String query = "New York";
        Address mockAddress = mock(Address.class);
        when(mockAddress.getLatitude()).thenReturn(40.7128);
        when(mockAddress.getLongitude()).thenReturn(-74.0060);
        
        List<Address> addressList = Collections.singletonList(mockAddress);
        when(mockGoogleMapsDataSource.geocodeLocation(query)).thenReturn(addressList);

        // Act
        LiveData<Location> result = placeRepository.searchLocation(query);
        
        // Wait briefly for executor service
        Thread.sleep(100);

        // Assert
        assertNotNull(result);
        Location location = result.getValue();
        assertNotNull(location);
        assertEquals(40.7128, location.latitude, 0.0001);
        assertEquals(-74.0060, location.longitude, 0.0001);
    }

    @Test
    public void searchLocation_emptyResults_returnsNull() throws IOException, InterruptedException {
        // Arrange
        String query = "Unknown Place 12345";
        when(mockGoogleMapsDataSource.geocodeLocation(query)).thenReturn(new ArrayList<>());

        // Act
        LiveData<Location> result = placeRepository.searchLocation(query);

        // Wait briefly for executor service
        Thread.sleep(100);

        // Assert
        assertNull(result.getValue());
    }

    @Test
    public void searchLocation_ioException_returnsNull() throws IOException, InterruptedException {
        // Arrange
        String query = "Error Place";
        when(mockGoogleMapsDataSource.geocodeLocation(query)).thenThrow(new IOException("Geocoding failed"));

        // Act
        LiveData<Location> result = placeRepository.searchLocation(query);

        // Wait briefly for executor service
        Thread.sleep(100);

        // Assert
        assertNull(result.getValue());
    }

    // --- searchPlaces Tests ---

    @Test
    public void searchPlaces_nullQuery_returnsEmptyList() {
        // Act
        LiveData<List<Place>> result = placeRepository.searchPlaces(null);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void searchPlaces_emptyQuery_returnsEmptyList() {
        // Act
        LiveData<List<Place>> result = placeRepository.searchPlaces("");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void searchPlaces_validQuery_successReturnsPlaces() throws InterruptedException {
        // Arrange
        String query = "Science";

        // Act
        LiveData<List<Place>> result = placeRepository.searchPlaces(query);
        
        // Wait for the mock 200ms delay + buffer
        Thread.sleep(300);

        // Assert
        assertNotNull(result);
        List<Place> places = result.getValue();
        assertNotNull("Places list should not be null", places);
        assertEquals(2, places.size()); // Both 'University of Science' and 'Social Sciences' contain 'science'
        
        Place place1 = places.get(0);
        assertEquals("mock_1", place1.id);
        assertEquals("University of Science", place1.name);
    }
}
