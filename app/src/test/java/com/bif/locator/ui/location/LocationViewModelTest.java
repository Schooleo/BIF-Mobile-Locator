package com.bif.locator.ui.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.bif.locator.domain.model.Location;
import com.bif.locator.domain.repository.ILocationRepository;
import com.bif.locator.domain.repository.LocationCallback;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LocationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private ILocationRepository locationRepository;

    @Mock
    private Observer<Location> locationObserver;

    @Mock
    private Observer<String> statusObserver;

    @Mock
    private Observer<Boolean> trackingObserver;

    private LocationViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new LocationViewModel(locationRepository);
        viewModel.currentLocation.observeForever(locationObserver);
        viewModel.statusText.observeForever(statusObserver);
        viewModel.isTracking.observeForever(trackingObserver);
    }

    @Test
    public void fetchCurrentLocation_whenCalled_callsRequestLocationUpdates() {
        // Act
        viewModel.fetchCurrentLocation();

        // Assert
        verify(locationRepository).requestLocationUpdates(any(LocationCallback.class));
        assertEquals("Fetching location...", viewModel.statusText.getValue());
    }

    @Test
    public void startTracking_whenCalled_setsIsTrackingTrueAndCallsRepository() {
        // Act
        viewModel.startTracking();

        // Assert
        verify(locationRepository).requestLocationUpdates(any(LocationCallback.class));
        assertTrue(viewModel.isTracking.getValue());
        assertEquals("Tracking location...", viewModel.statusText.getValue());
    }

    @Test
    public void stopTracking_whenTrackingStarted_setsIsTrackingFalseAndCallsRemoveUpdates() {
        // Arrange - start tracking first
        viewModel.startTracking();

        // Act
        viewModel.stopTracking();

        // Assert
        verify(locationRepository).removeLocationUpdates(any(LocationCallback.class));
        assertFalse(viewModel.isTracking.getValue());
        assertEquals("Tracking stopped", viewModel.statusText.getValue());
    }

    @Test
    public void onLocationResult_validLocation_updatesCurrentLocationLiveData() {
        // Arrange - capture the callback
        ArgumentCaptor<LocationCallback> callbackCaptor = 
            ArgumentCaptor.forClass(LocationCallback.class);

        // Act - start tracking to register callback
        viewModel.startTracking();

        // Capture the callback that was passed to repository
        verify(locationRepository).requestLocationUpdates(callbackCaptor.capture());

        // Simulate location result
        Location testLocation = new Location();
        testLocation.latitude = 10.7626636;
        testLocation.longitude = 106.6823091;

        callbackCaptor.getValue().onLocationResult(testLocation);

        // Assert
        Location result = viewModel.currentLocation.getValue();
        assertNotNull(result);
        assertEquals(10.7626636, result.latitude, 0.0001);
        assertEquals(106.6823091, result.longitude, 0.0001);
    }

    @Test
    public void onError_gpsUnavailable_updatesStatusTextWithError() {
        // Arrange
        ArgumentCaptor<LocationCallback> callbackCaptor = 
            ArgumentCaptor.forClass(LocationCallback.class);

        viewModel.startTracking();
        verify(locationRepository).requestLocationUpdates(callbackCaptor.capture());

        // Act - simulate error
        String errorMessage = "GPS not available";
        callbackCaptor.getValue().onError(errorMessage);

        // Assert
        assertEquals("Error: " + errorMessage, viewModel.statusText.getValue());
    }

    @Test
    public void init_defaultState_isTrackingIsFalse() {
        // Assert
        assertFalse(viewModel.isTracking.getValue());
    }
}