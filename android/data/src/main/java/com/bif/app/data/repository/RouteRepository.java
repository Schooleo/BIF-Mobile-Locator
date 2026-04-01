package com.bif.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.route.RouteRequestDto;
import com.bif.app.core.network.dto.route.RouteResponseDto;
import com.bif.app.core.network.dto.route.RouteWaypointDto;
import com.bif.app.core.utils.DistanceUtils;
import com.bif.app.data.routing.OfflineRoutingEngine;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.repository.IRouteRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class RouteRepository implements IRouteRepository {

    private static final double VIETNAM_MIN_LAT = 8.56;
    private static final double VIETNAM_MAX_LAT = 23.39;
    private static final double VIETNAM_MIN_LON = 102.14;
    private static final double VIETNAM_MAX_LON = 109.46;
    private static final String OFFLINE_CITY_MAP_FILE = "offline-map/city-map.osm.pbf";
    private static final long MIN_OFFLINE_CITY_MAP_BYTES = 1024L;
    private static final long MAX_OFFLINE_CITY_MAP_DOWNLOAD_BYTES = 512L * 1024L * 1024L;

    private final RestApiService restApiService;
    private final OfflineRoutingEngine offlineRoutingEngine;
    private final NetworkMonitor networkMonitor;
    private final Context appContext;
    private final ExecutorService mapDownloadExecutor;
    private final AtomicBoolean isCityMapDownloadInProgress;
    private final MutableLiveData<OfflineMapDownloadState> offlineMapDownloadState;
    private final ExecutorService executorService;

    private static final class DownloadResult {
        final boolean success;
        final String errorMessage;

        private DownloadResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        static DownloadResult success() {
            return new DownloadResult(true, null);
        }

        static DownloadResult failed(String errorMessage) {
            return new DownloadResult(false, errorMessage);
        }
    }

    @Inject
    public RouteRepository(RestApiService restApiService,
            OfflineRoutingEngine offlineRoutingEngine,
            NetworkMonitor networkMonitor,
            @ApplicationContext Context appContext) {
        this.restApiService = restApiService;
        this.offlineRoutingEngine = offlineRoutingEngine;
        this.networkMonitor = networkMonitor;
        this.appContext = appContext;
        this.mapDownloadExecutor = Executors.newSingleThreadExecutor();
        this.isCityMapDownloadInProgress = new AtomicBoolean(false);
        this.offlineMapDownloadState = new MutableLiveData<>(
            hasOfflineCityMap()
                ? OfflineMapDownloadState.alreadyDownloaded()
                : OfflineMapDownloadState.idle());
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<Route> getRoute(List<Location> waypoints) {
        MutableLiveData<Route> result = new MutableLiveData<>();
        executorService.execute(() -> result.postValue(buildRoute(waypoints)));
        return result;
    }

    @Override
    public LiveData<Boolean> observeOnlineStatus() {
        return networkMonitor.observeConnectivity();
    }

    @Override
    public LiveData<OfflineMapDownloadState> observeOfflineCityMapDownloadState() {
        return offlineMapDownloadState;
    }

    @Override
    public void requestOfflineCityMapDownload(Location origin) {
        if (origin == null) {
            offlineMapDownloadState.postValue(
                    OfflineMapDownloadState.failed("Current location unavailable"));
            return;
        }

        if (!isInVietnam(origin)) {
            offlineMapDownloadState.postValue(
                    OfflineMapDownloadState.failed("Location outside supported area"));
            return;
        }

        if (hasOfflineCityMap()) {
            offlineMapDownloadState.postValue(OfflineMapDownloadState.alreadyDownloaded());
            return;
        }

        if (!networkMonitor.isOnline()) {
            offlineMapDownloadState.postValue(
                    OfflineMapDownloadState.failed("No internet connection"));
            return;
        }

        if (!isCityMapDownloadInProgress.compareAndSet(false, true)) {
            return;
        }

        offlineMapDownloadState.postValue(OfflineMapDownloadState.downloading(0, true));
        mapDownloadExecutor.execute(() -> {
            try {
                DownloadResult result = downloadCityMap(origin);
                if (result.success || hasOfflineCityMap()) {
                    offlineMapDownloadState.postValue(OfflineMapDownloadState.completed());
                } else {
                    offlineMapDownloadState.postValue(
                            OfflineMapDownloadState.failed(
                                    result.errorMessage != null
                                            ? result.errorMessage
                                            : "Map data download failed"));
                }
            } finally {
                isCityMapDownloadInProgress.set(false);
            }
        });
    }

    private Route buildRoute(List<Location> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return null;
        }

        for (Location waypoint : waypoints) {
            if (waypoint == null || !isInVietnam(waypoint)) {
                return null;
            }
        }

        List<DistanceUtils.GeoPoint> geoPoints = toGeoPoints(waypoints);
        double totalDistanceKm = DistanceUtils.calculateTotalDistanceKm(geoPoints);
        String profile = DistanceUtils.determineProfile(totalDistanceKm);

        Route onlineRoute = fetchOnlineRoute(waypoints, profile);
        if (onlineRoute != null) {
            return onlineRoute;
        }

        return fetchEmbeddedOfflineRoute(waypoints, profile);
    }

    private Route fetchEmbeddedOfflineRoute(List<Location> waypoints, String profile) {
        File mapDataFile = offlineCityMapFile();
        if (!hasOfflineCityMap()) {
            return null;
        }
        return offlineRoutingEngine.route(waypoints, profile, mapDataFile);
    }

    private boolean hasOfflineCityMap() {
        File cityMap = offlineCityMapFile();
        return cityMap.exists() && cityMap.isFile() && cityMap.length() >= MIN_OFFLINE_CITY_MAP_BYTES;
    }

    private File offlineCityMapFile() {
        return new File(appContext.getFilesDir(), OFFLINE_CITY_MAP_FILE);
    }

    private DownloadResult downloadCityMap(Location origin) {
        Response<ResponseBody> response;
        try {
            response = restApiService.downloadCityMap(origin.latitude, origin.longitude).execute();
        } catch (IOException ignored) {
            return DownloadResult.failed("Network error while downloading map data");
        }

        if (!response.isSuccessful()) {
            return DownloadResult.failed(errorForDownloadStatus(response.code()));
        }

        if (response.body() == null) {
            return DownloadResult.failed("Map data unavailable from server");
        }

        File cityMap = new File(appContext.getFilesDir(), OFFLINE_CITY_MAP_FILE);
        File parent = cityMap.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        File tempFile = new File(cityMap.getAbsolutePath() + ".tmp");
        long bytesWritten = 0L;

        try (ResponseBody body = response.body();
             InputStream in = body.byteStream();
             FileOutputStream out = new FileOutputStream(tempFile)) {
            long contentLength = body.contentLength();
            if (contentLength > MAX_OFFLINE_CITY_MAP_DOWNLOAD_BYTES) {
                return DownloadResult.failed("Map data file is too large to download");
            }

            boolean indeterminate = contentLength <= 0L;
            int lastProgress = 0;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                bytesWritten += read;

                if (bytesWritten > MAX_OFFLINE_CITY_MAP_DOWNLOAD_BYTES) {
                    //noinspection ResultOfMethodCallIgnored
                    tempFile.delete();
                    return DownloadResult.failed("Map data file is too large to download");
                }

                if (!indeterminate) {
                    int progress = (int) Math.min(100L, (bytesWritten * 100L) / contentLength);
                    if (progress != lastProgress) {
                        lastProgress = progress;
                        offlineMapDownloadState.postValue(
                                OfflineMapDownloadState.downloading(progress, false));
                    }
                }
            }
            out.flush();
        } catch (IOException ignored) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            return DownloadResult.failed("Failed to save map data to device");
        }

        if (tempFile.length() < MIN_OFFLINE_CITY_MAP_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            tempFile.delete();
            return DownloadResult.failed("Downloaded map data is invalid");
        }

        if (cityMap.exists()) {
            //noinspection ResultOfMethodCallIgnored
            cityMap.delete();
        }

        boolean renamed = tempFile.renameTo(cityMap);
        if (renamed) {
            offlineMapDownloadState.postValue(OfflineMapDownloadState.downloading(100, false));
            return DownloadResult.success();
        }
        return DownloadResult.failed("Failed to finalize downloaded map data");
    }

    private String errorForDownloadStatus(int statusCode) {
        if (statusCode == 400) {
            return "Map download is only supported in Vietnam";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "Authentication required for map download";
        }
        if (statusCode == 404) {
            return "Map data not found on server";
        }
        return "Map download failed (HTTP " + statusCode + ")";
    }

    private boolean isInVietnam(Location location) {
        return location.latitude >= VIETNAM_MIN_LAT
                && location.latitude <= VIETNAM_MAX_LAT
                && location.longitude >= VIETNAM_MIN_LON
                && location.longitude <= VIETNAM_MAX_LON;
    }

    private Route fetchOnlineRoute(List<Location> waypoints, String profile) {
        try {
            Response<RouteResponseDto> response = restApiService
                    .routeTrip(toRequest(waypoints, profile))
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            RouteResponseDto body = response.body();
            String resolvedProfile = body.profile == null || body.profile.isBlank()
                    ? profile
                    : body.profile;

            return new Route(
                    body.distanceMeters,
                    body.durationSeconds,
                    body.geometry != null ? body.geometry.toString() : null,
                    resolvedProfile,
                    Route.SOURCE_ONLINE);
        } catch (IOException ignored) {
            return null;
        }
    }

    private RouteRequestDto toRequest(List<Location> waypoints, String profile) {
        RouteRequestDto request = new RouteRequestDto();
        request.profile = profile;

        List<RouteWaypointDto> dtoWaypoints = new ArrayList<>();
        for (Location waypoint : waypoints) {
            if (waypoint == null) {
                continue;
            }
            dtoWaypoints.add(new RouteWaypointDto(waypoint.latitude, waypoint.longitude));
        }
        request.waypoints = dtoWaypoints;
        return request;
    }

    private List<DistanceUtils.GeoPoint> toGeoPoints(List<Location> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            return Collections.emptyList();
        }

        List<DistanceUtils.GeoPoint> points = new ArrayList<>();
        for (Location waypoint : waypoints) {
            if (waypoint == null) {
                continue;
            }
            points.add(new DistanceUtils.GeoPoint(waypoint.latitude, waypoint.longitude));
        }
        return points;
    }

}
