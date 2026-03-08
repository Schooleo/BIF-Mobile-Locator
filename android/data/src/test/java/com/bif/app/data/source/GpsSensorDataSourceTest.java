package com.bif.app.data.source;

import android.content.Context;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.Task;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GpsSensorDataSourceTest {

    @Mock private Context mockContext;
    @Mock private FusedLocationProviderClient mockFusedLocationClient;
    @Mock private Task<android.location.Location> mockLocationTask;
    @Mock private Looper mockLooper;

    private MockedStatic<LocationServices> mockedLocationServices;
    private MockedStatic<Looper> mockedLooper;

    private GpsSensorDataSource dataSource;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        
        mockedLocationServices = mockStatic(LocationServices.class);
        mockedLocationServices.when(() -> LocationServices.getFusedLocationProviderClient(mockContext))
                .thenReturn(mockFusedLocationClient);
                
        mockedLooper = mockStatic(Looper.class);
        mockedLooper.when(Looper::getMainLooper).thenReturn(mockLooper);

        dataSource = new GpsSensorDataSource(mockContext);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedLocationServices != null) {
            mockedLocationServices.close();
        }
        if (mockedLooper != null) {
            mockedLooper.close();
        }
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    public void getCurrentLocation_callsFusedClient() {
        when(mockFusedLocationClient.getCurrentLocation(eq(Priority.PRIORITY_HIGH_ACCURACY), any()))
                .thenReturn(mockLocationTask);

        Task<android.location.Location> result = dataSource.getCurrentLocation();

        assertNotNull(result);
        verify(mockFusedLocationClient).getCurrentLocation(eq(Priority.PRIORITY_HIGH_ACCURACY), any());
    }

    @Test
    public void requestLocationUpdates_callsFusedClient() {
        LocationCallback mockCallback = mock(LocationCallback.class);

        dataSource.requestLocationUpdates(mockCallback);

        ArgumentCaptor<LocationRequest> requestCaptor = ArgumentCaptor.forClass(LocationRequest.class);
        verify(mockFusedLocationClient).requestLocationUpdates(requestCaptor.capture(), eq(mockCallback), eq(mockLooper));
        
        LocationRequest request = requestCaptor.getValue();
        assertEquals(Priority.PRIORITY_HIGH_ACCURACY, request.getPriority());
    }

    @Test
    public void removeLocationUpdates_cancelsUpdates() {
        LocationCallback mockCallback = mock(LocationCallback.class);

        dataSource.removeLocationUpdates(mockCallback);

        verify(mockFusedLocationClient).removeLocationUpdates(mockCallback);
    }
}
