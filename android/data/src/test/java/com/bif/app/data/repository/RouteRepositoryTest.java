package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.bif.app.core.network.RestApiService;
import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.routing.OfflineRoutingEngine;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Route;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import retrofit2.Call;

public class RouteRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private RestApiService mockRestApiService;
    @Mock
    private OfflineRoutingEngine mockOfflineRoutingEngine;
    @Mock
    private NetworkMonitor mockNetworkMonitor;
    @Mock
    private Context mockContext;

    private RouteRepository routeRepository;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getFilesDir()).thenReturn(temporaryFolder.getRoot());

        Call<com.bif.app.core.network.dto.route.RouteResponseDto> call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("offline"));
        when(mockRestApiService.routeTrip(any())).thenReturn(call);

        routeRepository = new RouteRepository(
                mockRestApiService,
                mockOfflineRoutingEngine,
                mockNetworkMonitor,
                mockContext);
    }

    @Test
    public void getRoute_onlineFailsAndOfflineMapExists_usesEmbeddedEngine() throws Exception {
        createOfflineMapFile();
        when(mockOfflineRoutingEngine.isReady(any())).thenReturn(true);

        Route offlineRoute = new Route(
                1200.0,
                300.0,
                "{\"type\":\"LineString\",\"coordinates\":[[106.7,10.7],[106.8,10.8]]}",
                "bike",
                Route.SOURCE_BROUTER);
        when(mockOfflineRoutingEngine.route(any(), any(), any())).thenReturn(offlineRoute);

        List<Location> waypoints = List.of(
                new Location(10.7769, 106.7009),
                new Location(10.8231, 106.6297));

        Route result = LiveDataTestUtil.getOrAwaitValue(routeRepository.getRoute(waypoints));

        assertNotNull(result);
        assertEquals(Route.SOURCE_BROUTER, result.getSource());
        verify(mockOfflineRoutingEngine).route(any(), eq("bicycle.brf"), any());
    }

    @Test
    public void getRoute_waypointOutsideVietnam_returnsNullWithoutCallingServices() throws Exception {
        List<Location> waypoints = List.of(
                new Location(10.7769, 106.7009),
                new Location(40.7128, -74.0060));

        Route result = LiveDataTestUtil.getOrAwaitValue(routeRepository.getRoute(waypoints));

        assertNull(result);
        verify(mockRestApiService, never()).routeTrip(any());
        verify(mockOfflineRoutingEngine, never()).route(any(), any(), any());
    }

    @Test
    public void requestOfflineCityMap_outsideSupportedArea_postsFailedState() throws Exception {
        routeRepository.requestOfflineCityMapDownload(new Location(40.7128, -74.0060));

        OfflineMapDownloadState state = LiveDataTestUtil
                .getOrAwaitValue(routeRepository.observeOfflineCityMapDownloadState());

        assertEquals(OfflineMapDownloadState.Status.FAILED, state.status);
        assertEquals("Location outside supported area", state.errorMessage);
    }

    private void createOfflineMapFile() throws IOException {
        File root = new File(temporaryFolder.getRoot(), "offline-map/brouter-cache");
        File profilesDir = new File(root, "profiles2");
        File segmentsDir = new File(root, "segments4");
        profilesDir.mkdirs();
        segmentsDir.mkdirs();

        try (FileOutputStream lookups = new FileOutputStream(new File(profilesDir, "lookups.dat"))) {
            lookups.write(new byte[2048]);
            lookups.flush();
        }

        try (FileOutputStream profile = new FileOutputStream(new File(profilesDir, "bicycle.brf"))) {
            profile.write("assign validForBikes = true\n".getBytes(StandardCharsets.UTF_8));
            profile.flush();
        }

        try (FileOutputStream rd5 = new FileOutputStream(new File(segmentsDir, "E105_N10.rd5"))) {
            rd5.write(new byte[2048]);
            rd5.flush();
        }
    }
}
