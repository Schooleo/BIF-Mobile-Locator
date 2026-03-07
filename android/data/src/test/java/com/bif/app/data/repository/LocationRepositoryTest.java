package com.bif.app.data.repository;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.data.source.GpsSensorDataSource;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.repository.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.util.Collections;

public class LocationRepositoryTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock private GpsSensorDataSource mockGpsSensorDataSource;
    @Mock private GoogleMapsDataSource mockGoogleMapsDataSource;
    @Mock private Task<android.location.Location> mockLocationTask;

    private LocationRepository repository;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        // Setup mock chain for getCurrentLocation
        when(mockGpsSensorDataSource.getCurrentLocation())
                .thenReturn(mockLocationTask);
        when(mockLocationTask.addOnSuccessListener(any()))
                .thenReturn(mockLocationTask);
        when(mockLocationTask.addOnFailureListener(any()))
                .thenReturn(mockLocationTask);

        repository = new LocationRepository(mockGpsSensorDataSource, mockGoogleMapsDataSource);
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void constructor_withValidDependencies_instantiatesSuccessfully() {
        assertNotNull(repository);
    }

    @Test
    public void getCurrentLocation_validRequest_returnsLiveData() {
        // Act
        LiveData<Location> result = repository.getCurrentLocation();

        // Assert
        assertNotNull(result);
    }

    @Test
    public void getCurrentLocation_validRequest_callsGpsDataSource() {
        // Act
        repository.getCurrentLocation();

        // Assert
        verify(mockGpsSensorDataSource).getCurrentLocation();
    }

    @Test
    public void getCurrentLocation_gpsSuccess_postsLocation() {
        // Arrange
        android.location.Location mockAndroidLocation = mock(android.location.Location.class);
        when(mockAndroidLocation.getLatitude()).thenReturn(10.762622);
        when(mockAndroidLocation.getLongitude()).thenReturn(106.660172);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<OnSuccessListener<android.location.Location>> captor =
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<OnSuccessListener<android.location.Location>> captor =
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
    public void removeLocationUpdates_noPriorRegistration_doesNotCrash() {
        // Act & Assert - no exception thrown
        LocationCallback mockCallback = mock(LocationCallback.class);
        repository.removeLocationUpdates(mockCallback);
        assertTrue(true);
    }

    @Test
    public void requestLocationUpdates_validCallback_callsGpsDataSource() {
        // Arrange
        LocationCallback mockCallback = mock(LocationCallback.class);

        // Act
        repository.requestLocationUpdates(mockCallback);

        // Assert
        verify(mockGpsSensorDataSource).requestLocationUpdates(any(com.google.android.gms.location.LocationCallback.class));
    }
    
    @Test
    public void requestLocationUpdates_callbackInvoked_mapsCorrectly() {
        // Arrange
        LocationCallback mockCallback = mock(LocationCallback.class);
        ArgumentCaptor<com.google.android.gms.location.LocationCallback> captor = 
                ArgumentCaptor.forClass(com.google.android.gms.location.LocationCallback.class);

        repository.requestLocationUpdates(mockCallback);
        verify(mockGpsSensorDataSource).requestLocationUpdates(captor.capture());
        
        com.google.android.gms.location.LocationCallback internalCallback = captor.getValue();
        
        android.location.Location mockAndroidLoc = mock(android.location.Location.class);
        when(mockAndroidLoc.getLatitude()).thenReturn(20.0);
        when(mockAndroidLoc.getLongitude()).thenReturn(30.0);
        
        LocationResult mockResult = mock(LocationResult.class);
        when(mockResult.getLastLocation()).thenReturn(mockAndroidLoc);
        
        // Act
        internalCallback.onLocationResult(mockResult);
        
        // Assert
        ArgumentCaptor<Location> domainLocationCaptor = ArgumentCaptor.forClass(Location.class);
        verify(mockCallback).onLocationResult(domainLocationCaptor.capture());
        
        Location resultingLocation = domainLocationCaptor.getValue();
        assertNotNull(resultingLocation);
        assertEquals(20.0, resultingLocation.latitude, 0.0001);
        assertEquals(30.0, resultingLocation.longitude, 0.0001);
    }

    @Test
    public void removeLocationUpdates_afterRegistration_removesCorrectCallback() {
        // Arrange
        LocationCallback mockCallback = mock(LocationCallback.class);
        ArgumentCaptor<com.google.android.gms.location.LocationCallback> captor = 
                ArgumentCaptor.forClass(com.google.android.gms.location.LocationCallback.class);

        repository.requestLocationUpdates(mockCallback);
        verify(mockGpsSensorDataSource).requestLocationUpdates(captor.capture());
        
        com.google.android.gms.location.LocationCallback internalCallback = captor.getValue();
        
        // Act
        repository.removeLocationUpdates(mockCallback);
        
        // Assert
        verify(mockGpsSensorDataSource).removeLocationUpdates(internalCallback);
    }
}
