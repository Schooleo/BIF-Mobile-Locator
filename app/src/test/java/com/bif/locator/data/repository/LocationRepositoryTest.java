package com.bif.locator.data.repository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.bif.locator.domain.model.Location;
import com.bif.locator.domain.model.Place;
import com.bif.locator.domain.repository.LocationCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class LocationRepositoryTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock private Context mockContext;
    @Mock private FusedLocationProviderClient mockFusedClient;
    @Mock private PlacesClient mockPlacesClient;
    @Mock private ExecutorService mockExecutor;
    @Mock private Task<android.location.Location> mockLocationTask;
    private LocationRepository repository;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mock chain cho getCurrentLocation
        when(mockFusedClient.getCurrentLocation(anyInt(), any()))
                .thenReturn(mockLocationTask);
        when(mockLocationTask.addOnSuccessListener(any()))
                .thenReturn(mockLocationTask);
        when(mockLocationTask.addOnFailureListener(any()))
                .thenReturn(mockLocationTask);

        repository = new LocationRepository(
                mockContext, mockFusedClient, mockPlacesClient, mockExecutor);
    }

    @Test
    public void constructor_withValidDependencies_instantiatesSuccessfully() {
        LocationRepository repo = new LocationRepository(
                mockContext, mockFusedClient, mockPlacesClient, mockExecutor);
        assertNotNull(repo);
    }

    @Test
    public void getCurrentLocation_validRequest_returnsLiveData()
    {
        // Act
        LiveData<Location> result = repository.getCurrentLocation();

        // Assert
        assertNotNull(result);
    }

    @Test
    public void getCurrentLocation_validRequest_callsFusedClient() {
        // Act
        repository.getCurrentLocation();

        // Assert
        verify(mockFusedClient).getCurrentLocation(anyInt(), any());
    }

    @Test
    public void getCurrentLocation_gpsSuccess_postsLocation() {
        // Arrange
        android.location.Location mockAndroidLocation = mock(android.location.Location.class);
        when(mockAndroidLocation.getLatitude()).thenReturn(10.762622);
        when(mockAndroidLocation.getLongitude()).thenReturn(106.660172);

        ArgumentCaptor<OnSuccessListener> captor =
                ArgumentCaptor.forClass(OnSuccessListener.class);

        // Act
        LiveData<Location> result = repository.getCurrentLocation();
        verify(mockLocationTask).addOnSuccessListener(captor.capture());
        captor.getValue().onSuccess(mockAndroidLocation);

        // Assert
        Location location = result.getValue();
        assertNotNull(location);
        assertEquals(10.762622, location.latitude, 0.0001);
        assertEquals(106.660172, location.longitude, 0.0001);
    }

    @Test
    public void getCurrentLocation_gpsReturnsNull_postsNull() {
        // Arrange
        ArgumentCaptor<OnSuccessListener> captor =
                ArgumentCaptor.forClass(OnSuccessListener.class);

        // Act
        LiveData<Location> result = repository.getCurrentLocation();
        verify(mockLocationTask).addOnSuccessListener(captor.capture());
        captor.getValue().onSuccess(null);

        // Assert
        assertNull(result.getValue());
    }

    @Test
    public void getCurrentLocation_gpsError_postsNull() {
        // Arrange
        ArgumentCaptor<OnFailureListener> captor =
                ArgumentCaptor.forClass(OnFailureListener.class);

        // Act
        LiveData<Location> result = repository.getCurrentLocation();
        verify(mockLocationTask).addOnFailureListener(captor.capture());
        captor.getValue().onFailure(new Exception("GPS Error"));

        // Assert
        assertNull(result.getValue());
    }

    @Test
    public void searchPlaces_nullQuery_returnsEmptyList() {
        // Act
        LiveData<List<Place>> result = repository.searchPlaces(null);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void searchPlaces_emptyQuery_returnsEmptyList() {
        // Act
        LiveData<List<Place>> result = repository.searchPlaces("");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getValue());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    public void removeLocationUpdates_noPriorRegistration_doesNotCrash() {
        // Act & Assert - no exception thrown
        LocationCallback mockCallback = mock(LocationCallback.class);
        repository.removeLocationUpdates(mockCallback);
        assertTrue(true);
    }

    @Test
    public void requestLocationUpdates_validCallback_callsFusedClient() {
        // Arrange
        LocationCallback mockCallback = mock(LocationCallback.class);

        @SuppressWarnings("unchecked")
        Task<Void> mockTask = mock(Task.class);

        when(mockFusedClient.requestLocationUpdates(
                any(LocationRequest.class),
                any(com.google.android.gms.location.LocationCallback.class),
                nullable(Looper.class)))
                .thenReturn(mockTask);
        // Act
        repository.requestLocationUpdates(mockCallback);
        // Assert
        verify(mockFusedClient).requestLocationUpdates(
                any(LocationRequest.class),
                any(com.google.android.gms.location.LocationCallback.class),
                nullable(Looper.class));
    }
}
