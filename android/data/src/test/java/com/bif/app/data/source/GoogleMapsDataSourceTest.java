package com.bif.app.data.source;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoogleMapsDataSourceTest {

    @Mock private Context mockContext;
    @Mock private PlacesClient mockPlacesClient;
    @Mock private Task<FindAutocompletePredictionsResponse> mockAutocompleteTask;
    @Mock private Task<FetchPlaceResponse> mockFetchPlaceTask;

    private MockedStatic<Places> mockedPlaces;
    private MockedConstruction<Geocoder> mockedGeocoderConstruction;

    private GoogleMapsDataSource dataSource;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        
        mockedPlaces = mockStatic(Places.class);
        mockedPlaces.when(() -> Places.createClient(mockContext))
                .thenReturn(mockPlacesClient);
                
        mockedGeocoderConstruction = mockConstruction(Geocoder.class);

        dataSource = new GoogleMapsDataSource(mockContext);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedPlaces != null) {
            mockedPlaces.close();
        }
        if (mockedGeocoderConstruction != null) {
            mockedGeocoderConstruction.close();
        }
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void getAutocompletePredictions_validQuery_callsPlacesClient() {
        when(mockPlacesClient.findAutocompletePredictions(any(FindAutocompletePredictionsRequest.class)))
                .thenReturn(mockAutocompleteTask);

        Task<FindAutocompletePredictionsResponse> result = dataSource.getAutocompletePredictions("Cafe");

        assertNotNull(result);
        ArgumentCaptor<FindAutocompletePredictionsRequest> captor = ArgumentCaptor.forClass(FindAutocompletePredictionsRequest.class);
        verify(mockPlacesClient).findAutocompletePredictions(captor.capture());
        assertEquals("Cafe", captor.getValue().getQuery());
    }

    @Test
    public void fetchPlaceDetails_validId_callsPlacesClient() {
        when(mockPlacesClient.fetchPlace(any(FetchPlaceRequest.class)))
                .thenReturn(mockFetchPlaceTask);

        Task<FetchPlaceResponse> result = dataSource.fetchPlaceDetails("place123");

        assertNotNull(result);
        ArgumentCaptor<FetchPlaceRequest> captor = ArgumentCaptor.forClass(FetchPlaceRequest.class);
        verify(mockPlacesClient).fetchPlace(captor.capture());
        assertEquals("place123", captor.getValue().getPlaceId());
        assertTrue(captor.getValue().getPlaceFields().size() > 0);
    }
    
    @Test
    public void geocodeLocation_validQuery_callsGeocoder() throws Exception {
        Geocoder mockGeocoder = mockedGeocoderConstruction.constructed().get(0);
        List<Address> mockList = Collections.singletonList(mock(Address.class));
        when(mockGeocoder.getFromLocationName("New York", 1)).thenReturn(mockList);

        List<Address> result = dataSource.geocodeLocation("New York");

        assertEquals(mockList, result);
        verify(mockGeocoder).getFromLocationName("New York", 1);
    }
}
