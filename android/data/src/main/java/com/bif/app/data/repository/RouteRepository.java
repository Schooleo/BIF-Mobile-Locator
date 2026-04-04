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
import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.repository.IRouteRepository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class RouteRepository implements IRouteRepository {

    private static final double VIETNAM_MIN_LAT = 8.56;
    private static final double VIETNAM_MAX_LAT = 23.39;
    private static final double VIETNAM_MIN_LON = 102.14;
    private static final double VIETNAM_MAX_LON = 109.46;
    private static final String OFFLINE_CITY_GRAPH_DIR = "offline-map/brouter-cache";
    private static final String OFFLINE_CITY_GRAPH_ARCHIVE = "offline-map/city-map-brouter-cache.zip";
    private static final String BROUTER_PROFILES_DIR = "profiles2";
    private static final String BROUTER_SEGMENTS_DIR = "segments4";
    private static final String BROUTER_LOOKUPS_FILE = "lookups.dat";
    private static final String CAR_PROFILE = "car-fast.brf";
    private static final String BICYCLE_PROFILE = "bicycle.brf";
    private static final String FOOT_PROFILE = "foot.brf";
    private static final long MIN_OFFLINE_CITY_MAP_BYTES = 1024L;
    private static final long MAX_OFFLINE_CITY_MAP_DOWNLOAD_BYTES = 256L * 1024L * 1024L;
    private static final long ONLINE_ROUTE_TIMEOUT_SECONDS = 8L;

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
            hasOfflineCityMapData()
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

        if (hasOfflineCityMapData()) {
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
                if (result.success || hasOfflineCityMapData()) {
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
        String onlineProfile = DistanceUtils.determineProfile(totalDistanceKm);
        String offlineProfile = mapOfflineProfile(onlineProfile);

        boolean offlineReady = hasOfflineCityMap();
        if (offlineReady) {
            Route offlineRoute = fetchEmbeddedOfflineRoute(waypoints, offlineProfile);
            if (offlineRoute != null) {
                return offlineRoute;
            }
        }

        if (!networkMonitor.isOnline()) {
            return null;
        }

        Route onlineRoute = fetchOnlineRoute(waypoints, onlineProfile);
        if (onlineRoute != null) {
            return onlineRoute;
        }

        if (!offlineReady && hasOfflineCityMap()) {
            return fetchEmbeddedOfflineRoute(waypoints, offlineProfile);
        }

        return null;
    }

    private Route fetchEmbeddedOfflineRoute(List<Location> waypoints, String profile) {
        File mapDataFile = offlineCityGraphDir();
        if (!hasOfflineCityMap()) {
            return null;
        }
        return offlineRoutingEngine.route(waypoints, profile, mapDataFile);
    }

    private boolean hasOfflineCityMapData() {
        return isGraphCacheDirectory(offlineCityGraphDir());
    }

    private boolean hasOfflineCityMap() {
        File cityGraphDir = offlineCityGraphDir();
        return hasOfflineCityMapData() && offlineRoutingEngine.isReady(cityGraphDir);
    }

    private File offlineCityGraphDir() {
        return new File(appContext.getFilesDir(), OFFLINE_CITY_GRAPH_DIR);
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

        File cityGraphDir = offlineCityGraphDir();
        File parent = cityGraphDir.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }

        File tempArchive = new File(appContext.getFilesDir(), OFFLINE_CITY_GRAPH_ARCHIVE + ".tmp");
        long bytesWritten = 0L;

        try (ResponseBody body = response.body();
             InputStream in = body.byteStream();
             FileOutputStream out = new FileOutputStream(tempArchive)) {
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
                    tempArchive.delete();
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
            tempArchive.delete();
            return DownloadResult.failed("Failed to save map data to device");
        }

        if (tempArchive.length() < MIN_OFFLINE_CITY_MAP_BYTES) {
            //noinspection ResultOfMethodCallIgnored
            tempArchive.delete();
            return DownloadResult.failed("Downloaded map data is invalid");
        }

        File stagingDir = new File(parent, "brouter-cache-staging");
        deleteRecursively(stagingDir);
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            //noinspection ResultOfMethodCallIgnored
            tempArchive.delete();
            return DownloadResult.failed("Failed to prepare map cache directory");
        }

        try {
            unzipArchive(tempArchive, stagingDir);
            File extractedGraphCache = findGraphCacheDirectory(stagingDir);
            if (extractedGraphCache == null) {
                return DownloadResult.failed("Downloaded map data is not a valid BRouter cache");
            }

            if (cityGraphDir.exists()) {
                deleteRecursively(cityGraphDir);
            }

            if (!moveDirectory(extractedGraphCache, cityGraphDir)) {
                return DownloadResult.failed("Failed to finalize downloaded map data");
            }

            offlineMapDownloadState.postValue(OfflineMapDownloadState.downloading(100, false));
            return DownloadResult.success();
        } catch (IOException ignored) {
            return DownloadResult.failed("Failed to extract map data on device");
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tempArchive.delete();
            deleteRecursively(stagingDir);
        }
    }

    private boolean isGraphCacheDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return false;
        }

        File profilesDir = new File(directory, BROUTER_PROFILES_DIR);
        File segmentsDir = new File(directory, BROUTER_SEGMENTS_DIR);
        File lookups = new File(profilesDir, BROUTER_LOOKUPS_FILE);
        return profilesDir.exists()
                && profilesDir.isDirectory()
                && segmentsDir.exists()
                && segmentsDir.isDirectory()
                && lookups.exists()
                && lookups.isFile()
                && hasFileWithExtension(profilesDir, ".brf")
                && hasFileWithExtension(segmentsDir, ".rd5");
    }

    private boolean hasFileWithExtension(File directory, String extension) {
        File[] children = directory.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private void unzipArchive(File archiveFile, File destinationDir) throws IOException {
        String destinationRoot = destinationDir.getCanonicalPath() + File.separator;
        try (ZipInputStream zipInputStream = new ZipInputStream(new java.io.FileInputStream(archiveFile))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                File output = new File(destinationDir, entry.getName());
                String outputPath = output.getCanonicalPath();
                if (!outputPath.startsWith(destinationRoot)) {
                    throw new IOException("Invalid zip entry path");
                }

                if (entry.isDirectory()) {
                    if (!output.exists() && !output.mkdirs()) {
                        throw new IOException("Failed to create directory while extracting map data");
                    }
                } else {
                    File outputParent = output.getParentFile();
                    if (outputParent != null && !outputParent.exists() && !outputParent.mkdirs()) {
                        throw new IOException("Failed to prepare output directory while extracting map data");
                    }

                    try (FileOutputStream outputStream = new FileOutputStream(output)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, read);
                        }
                        outputStream.flush();
                    }
                }
                zipInputStream.closeEntry();
            }
        }
    }

    private File findGraphCacheDirectory(File rootDirectory) {
        if (isGraphCacheDirectory(rootDirectory)) {
            return rootDirectory;
        }

        Deque<File> queue = new ArrayDeque<>();
        queue.add(rootDirectory);
        int maxVisited = 64;
        int visited = 0;

        while (!queue.isEmpty() && visited < maxVisited) {
            File current = queue.poll();
            visited++;

            File[] children = current.listFiles();
            if (children == null) {
                continue;
            }

            for (File child : children) {
                if (child.isDirectory()) {
                    if (isGraphCacheDirectory(child)) {
                        return child;
                    }
                    queue.add(child);
                }
            }
        }
        return null;
    }

    private boolean moveDirectory(File source, File destination) {
        if (source.equals(destination)) {
            return true;
        }
        return source.renameTo(destination);
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }

        //noinspection ResultOfMethodCallIgnored
        file.delete();
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
            retrofit2.Call<RouteResponseDto> call = restApiService
                .routeTrip(toRequest(waypoints, profile));
            call.timeout().timeout(ONLINE_ROUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            Response<RouteResponseDto> response = call.execute();

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

    private String mapOfflineProfile(String profile) {
        if (profile == null || profile.isBlank()) {
            return CAR_PROFILE;
        }

        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "foot":
            case "walk":
            case "walking":
                return FOOT_PROFILE;
            case "bike":
            case "bicycle":
            case "cycling":
                return BICYCLE_PROFILE;
            case "car":
            case "driving":
            default:
                return CAR_PROFILE;
        }
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

