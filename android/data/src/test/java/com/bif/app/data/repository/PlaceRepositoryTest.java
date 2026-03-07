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
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.net.SearchByTextResponse;

import org.junit.After;
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

    @Mock
    private Task<SearchByTextResponse> mockSearchTask;

    private PlaceRepository placeRepository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        placeRepository = new PlaceRepository(mockGoogleMapsDataSource);

        when(mockSearchTask.addOnSuccessListener(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockSearchTask);
        when(mockSearchTask.addOnFailureListener(org.mockito.ArgumentMatchers.any()))
                .thenReturn(mockSearchTask);
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
    @SuppressWarnings("unchecked")
    public void searchPlaces_validQuery_successReturnsPlaces() {
        // Arrange
        String query = "Coffee";
        when(mockGoogleMapsDataSource.searchPlaces(query)).thenReturn(mockSearchTask);

        com.google.android.libraries.places.api.model.Place mockGooglePlace = mock(com.google.android.libraries.places.api.model.Place.class);
        when(mockGooglePlace.getId()).thenReturn("p1");
        when(mockGooglePlace.getDisplayName()).thenReturn("Test Coffee Shop");
        when(mockGooglePlace.getFormattedAddress()).thenReturn("123 Coffee St");
        when(mockGooglePlace.getRating()).thenReturn(4.5);
        when(mockGooglePlace.getLocation()).thenReturn(new LatLng(10.0, 20.0));

        SearchByTextResponse mockResponse = mock(SearchByTextResponse.class);
        when(mockResponse.getPlaces()).thenReturn(Collections.singletonList(mockGooglePlace));

        ArgumentCaptor<OnSuccessListener<SearchByTextResponse>> captor = ArgumentCaptor.forClass(OnSuccessListener.class);

        // Act
        LiveData<List<Place>> result = placeRepository.searchPlaces(query);
        verify(mockSearchTask).addOnSuccessListener(captor.capture());
        
        // Trigger success callback
        captor.getValue().onSuccess(mockResponse);

        // Assert
        assertNotNull(result);
        List<Place> places = result.getValue();
        assertNotNull(places);
        assertEquals(1, places.size());
        
        Place place = places.get(0);
        assertEquals("p1", place.id);
        assertEquals("Test Coffee Shop", place.name);
        assertEquals("123 Coffee St", place.address);
        assertEquals(4.5, place.rating, 0.01);
        assertEquals(10.0, place.latitude, 0.0001);
        assertEquals(20.0, place.longitude, 0.0001);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void searchPlaces_validQuery_failureReturnsEmptyList() {
        // Arrange
        String query = "Coffee";
        when(mockGoogleMapsDataSource.searchPlaces(query)).thenReturn(mockSearchTask);

        ArgumentCaptor<OnFailureListener> captor = ArgumentCaptor.forClass(OnFailureListener.class);

        // Act
        LiveData<List<Place>> result = placeRepository.searchPlaces(query);
        verify(mockSearchTask).addOnFailureListener(captor.capture());
        
        // Trigger failure callback
        captor.getValue().onFailure(new Exception("Network Error"));

        // Assert
        assertNotNull(result);
        List<Place> places = result.getValue();
        assertNotNull(places);
        assertTrue(places.isEmpty());
    }
}
