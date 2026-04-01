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

    private final Object initializationLock = new Object();
    @Nullable
    private GraphHopper graphHopper;
    @Nullable
    private String loadedMapPath;
    private long loadedMapSize = -1L;
    private long loadedMapLastModified = -1L;

    @Inject
    public EmbeddedGraphHopperRoutingEngine() {
    }

    @Override
    public boolean isReady(@NonNull File mapDataFile) {
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

        GHResponse response = hopper.route(request);
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

            File graphCacheDir = new File(mapDataFile.getParentFile(), "gh-cache");
            if (!graphCacheDir.exists()) {
                // noinspection ResultOfMethodCallIgnored
                graphCacheDir.mkdirs();
            }

            try {
                GraphHopper hopper = new GraphHopper();
                hopper.setOSMFile(mapDataFile.getAbsolutePath());
                hopper.setGraphHopperLocation(graphCacheDir.getAbsolutePath());
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
                loadedMapSize = mapDataFile.length();
                loadedMapLastModified = mapDataFile.lastModified();
                Log.i(TAG, "Graph loaded from " + loadedMapPath + " into " + graphCacheDir.getAbsolutePath());
                return hopper;
            } catch (Exception ex) {
                Log.e(TAG, "Failed to initialize embedded GraphHopper", ex);
                graphHopper = null;
                loadedMapPath = null;
                loadedMapSize = -1L;
                loadedMapLastModified = -1L;
                return null;
            }
        }
    }

    private boolean isMapDataValid(@NonNull File mapDataFile) {
        return mapDataFile.exists() && mapDataFile.isFile() && mapDataFile.length() > 0;
    }

    private boolean isSameMapFile(@NonNull File mapDataFile) {
        return loadedMapPath != null
                && loadedMapPath.equals(mapDataFile.getAbsolutePath())
                && loadedMapSize == mapDataFile.length()
                && loadedMapLastModified == mapDataFile.lastModified();
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
