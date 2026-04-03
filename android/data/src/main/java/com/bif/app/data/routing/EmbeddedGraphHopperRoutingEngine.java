package com.bif.app.data.routing;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bif.app.domain.model.Route;
import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.ResponsePath;
import com.graphhopper.config.Profile;
import com.graphhopper.util.PointList;
import com.bif.app.domain.model.Location;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EmbeddedGraphHopperRoutingEngine implements OfflineRoutingEngine {

    private static final String TAG = "EmbeddedGHEngine";
    private static final String GRAPH_PROPERTIES_FILE = "properties";

    private final Object initializationLock = new Object();
    @Nullable
    private GraphHopper graphHopper;
    @Nullable
    private String loadedMapPath;
    private long loadedMapMarkerSize = -1L;
    private long loadedMapLastModified = -1L;
    private boolean graphUnsupportedForRuntime;

    @Inject
    public EmbeddedGraphHopperRoutingEngine() {
    }

    @Override
    public boolean isReady(@NonNull File mapDataFile) {
        if (graphUnsupportedForRuntime) {
            return false;
        }
        return isMapDataValid(mapDataFile);
    }

    @Nullable
    @Override
    public Route route(@NonNull List<Location> waypoints,
            @NonNull String profile,
            @NonNull File mapDataFile) {
        if (waypoints.size() < 2 || !isMapDataValid(mapDataFile)) {
            return null;
        }

        GraphHopper hopper = ensureGraphHopperLoaded(mapDataFile);
        if (hopper == null) {
            return null;
        }

        String resolvedProfile = resolveProfile(profile);
        GHRequest request = new GHRequest(
                waypoints.get(0).latitude,
                waypoints.get(0).longitude,
                waypoints.get(1).latitude,
                waypoints.get(1).longitude)
                .setProfile(resolvedProfile)
                .setLocale(Locale.getDefault());

        GHResponse response;
        try {
            response = hopper.route(request);
        } catch (Throwable throwable) {
            Log.e(TAG, "Embedded GraphHopper route execution failed", throwable);
            return null;
        }
        if (response.hasErrors()) {
            Log.w(TAG, "Route failed for profile=" + resolvedProfile + ", errors=" + response.getErrors());
            return null;
        }

        ResponsePath bestPath = response.getBest();
        if (bestPath == null) {
            return null;
        }

        PointList points = bestPath.getPoints();
        if (points == null || points.size() < 2) {
            return null;
        }

        String geometryJson = toLineStringJson(points);
        return new Route(
                Math.max(0.0, bestPath.getDistance()),
                Math.max(0.0, bestPath.getTime() / 1000.0),
                geometryJson,
                resolvedProfile,
                Route.SOURCE_GRAPHHOPPER);
    }

    @Nullable
    private GraphHopper ensureGraphHopperLoaded(@NonNull File mapDataFile) {
        synchronized (initializationLock) {
            if (graphUnsupportedForRuntime) {
                return null;
            }

            if (graphHopper != null && isSameMapFile(mapDataFile)) {
                return graphHopper;
            }

            GraphHopper previous = graphHopper;
            graphHopper = null;

            if (previous != null) {
                try {
                    previous.close();
                } catch (Exception ignored) {
                    // Continue and reinitialize a fresh instance.
                }
            }

            if (!isMapDataValid(mapDataFile)) {
                Log.w(TAG, "Graph cache directory is missing or invalid: " + mapDataFile.getAbsolutePath());
                loadedMapPath = null;
                loadedMapMarkerSize = -1L;
                loadedMapLastModified = -1L;
                return null;
            }

            try {
                GraphHopper hopper = new GraphHopper();
                hopper.setGraphHopperLocation(mapDataFile.getAbsolutePath());
                hopper.setEncodedValuesString(
                        "car_access,car_average_speed,"
                                + "bike_access,bike_average_speed,"
                                + "foot_access,foot_average_speed");
                hopper.setProfiles(
                        new Profile("car").setWeighting("fastest"),
                        new Profile("bike").setWeighting("fastest"),
                        new Profile("foot").setWeighting("fastest"));
                hopper.importOrLoad();

                graphHopper = hopper;
                loadedMapPath = mapDataFile.getAbsolutePath();
                File markerFile = markerFile(mapDataFile);
                loadedMapMarkerSize = markerFile.length();
                loadedMapLastModified = markerFile.lastModified();
                Log.i(TAG, "Graph loaded from prebuilt cache at " + loadedMapPath);
                return hopper;
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "Graph cache is too large for current device memory", oom);
                graphUnsupportedForRuntime = true;
                graphHopper = null;
                loadedMapPath = null;
                loadedMapMarkerSize = -1L;
                loadedMapLastModified = -1L;
                return null;
            } catch (NoClassDefFoundError noClassDefFoundError) {
                Log.e(TAG, "GraphHopper runtime dependency missing on Android classpath", noClassDefFoundError);
                graphUnsupportedForRuntime = true;
                graphHopper = null;
                loadedMapPath = null;
                loadedMapMarkerSize = -1L;
                loadedMapLastModified = -1L;
                return null;
            } catch (Throwable ex) {
                Log.e(TAG, "Failed to initialize embedded GraphHopper", ex);
                graphHopper = null;
                loadedMapPath = null;
                loadedMapMarkerSize = -1L;
                loadedMapLastModified = -1L;
                return null;
            }
        }
    }

    private boolean isMapDataValid(@NonNull File mapDataFile) {
        if (!mapDataFile.exists() || !mapDataFile.isDirectory()) {
            return false;
        }
        File markerFile = markerFile(mapDataFile);
        return markerFile.exists() && markerFile.isFile() && markerFile.length() > 0;
    }

    private boolean isSameMapFile(@NonNull File mapDataFile) {
        File markerFile = markerFile(mapDataFile);
        return loadedMapPath != null
                && loadedMapPath.equals(mapDataFile.getAbsolutePath())
                && loadedMapMarkerSize == markerFile.length()
                && loadedMapLastModified == markerFile.lastModified();
    }

    @NonNull
    private String resolveProfile(@Nullable String profile) {
        if (profile == null || profile.trim().isEmpty()) {
            return "car";
        }

        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "car":
            case "driving":
                return "car";
            case "bike":
            case "bicycle":
            case "cycling":
                return "bike";
            case "foot":
            case "walk":
            case "walking":
                return "foot";
            default:
                return "car";
        }
    }

    @NonNull
    private File markerFile(@NonNull File mapDataFile) {
        return new File(mapDataFile, GRAPH_PROPERTIES_FILE);
    }

    @NonNull
    private String toLineStringJson(@NonNull PointList points) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('[')
                    .append(points.getLon(i))
                    .append(',')
                    .append(points.getLat(i))
                    .append(']');
        }
        builder.append("]}");
        return builder.toString();
    }
}
