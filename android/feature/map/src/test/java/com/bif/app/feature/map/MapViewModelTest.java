package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.repository.ILocationRepository;
import com.bif.app.domain.repository.IMapRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MapViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private ILocationRepository locationRepository;

    @Mock
    private IMapRepository mapRepository;

    @Mock
    private Observer<Location> searchResultObserver;

    @Mock
    private Observer<String> statusTextObserver;

    private MapViewModel viewModel;

    @Before
    public void setUp() {
        // Stub searchLocation to return a valid LiveData to prevent NullPointerException during switchMap
        Mockito.when(locationRepository.searchLocation(ArgumentMatchers.anyString())).thenReturn(new MutableLiveData<>());
        
        viewModel = new MapViewModel(locationRepository, mapRepository);
        viewModel.searchResult.observeForever(searchResultObserver);
        viewModel.statusText.observeForever(statusTextObserver);
    }

    @Test
    public void searchLocation_validQuery_callsRepository() {
        // Arrange
        String query = "New York";

        // Act
        viewModel.searchLocation(query);

        // Assert
        Mockito.verify(locationRepository).searchLocation(query);
    }

    @Test
    public void setStatusText_updatesLiveData() {
        // Arrange
        String status = "Loading...";

        // Act
        viewModel.setStatusText(status);

        // Assert
        Mockito.verify(statusTextObserver).onChanged(status);
        assertEquals(status, viewModel.statusText.getValue());
    }

    @Test
    public void saveMapState_validInput_callsRepository() {
        // Arrange
        double lat = 10.0;
        double lng = 20.0;
        float zoom = 15.0f;

        // Act
        viewModel.saveMapState(lat, lng, zoom);

        // Assert
        ArgumentCaptor<MapState> captor = ArgumentCaptor.forClass(MapState.class);
        Mockito.verify(mapRepository).saveMapState(captor.capture());
        
        MapState savedState = captor.getValue();
        assertEquals(lat, savedState.latitude, 0.001);
        assertEquals(lng, savedState.longitude, 0.001);
        assertEquals(zoom, savedState.zoomLevel, 0.001);
    }

    @Test
    public void getLastMapState_repositoryReturnsState_returnsSameState() {
        // Arrange
        MapState expectedState = new MapState(10.0, 20.0, 15.0f);
        Mockito.when(mapRepository.getMapState()).thenReturn(expectedState);

        // Act
        MapState result = viewModel.getLastMapState();

        // Assert
        assertNotNull(result);
        assertEquals(expectedState, result);
    }

    @Test
    public void saveMapState_verifiesDataIntegrity() {
        // Arrange
        double expectedLat = 10.762622;
        double expectedLng = 106.682311;
        float expectedZoom = 15.5f;

        // Act
        viewModel.saveMapState(expectedLat, expectedLng, expectedZoom);

        // Assert
        ArgumentCaptor<MapState> captor = ArgumentCaptor.forClass(MapState.class);
        Mockito.verify(mapRepository).saveMapState(captor.capture());

        MapState captured = captor.getValue();
        assertEquals("Latitude mismatch", expectedLat, captured.latitude, 0.0001);
        assertEquals("Longitude mismatch", expectedLng, captured.longitude, 0.0001);
        assertEquals("Zoom mismatch", expectedZoom, captured.zoomLevel, 0.0001);
    }
}
