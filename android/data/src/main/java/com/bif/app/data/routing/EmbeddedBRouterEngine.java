package com.bif.app.data.routing;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Route;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import btools.router.OsmNodeNamed;
import btools.router.OsmPathElement;
import btools.router.OsmTrack;
import btools.router.RoutingContext;
import btools.router.RoutingEngine;

@Singleton
public class EmbeddedBRouterEngine implements OfflineRoutingEngine {

    private static final String TAG = "EmbeddedBRouter";
    private static final String PROFILES_DIR = "profiles2";
    private static final String SEGMENTS_DIR = "segments4";
    private static final String LOOKUPS_FILE = "lookups.dat";
    private static final String DEFAULT_CAR_PROFILE = "car-fast.brf";
    private static final String DEFAULT_BICYCLE_PROFILE = "bicycle.brf";
    private static final String DEFAULT_FOOT_PROFILE = "foot.brf";
    private static final int DEFAULT_MEMORY_CLASS_MB = 32;
    private static final int MIN_TRACK_POINTS = 2;

    @Inject
    public EmbeddedBRouterEngine() {
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

        File segmentDir = segmentDir(mapDataFile);
        File profileFile = resolveProfileFile(mapDataFile, profile);
        if (profileFile == null || !segmentDir.exists() || !segmentDir.isDirectory()) {
            return null;
        }

        List<OsmNodeNamed> routingWaypoints = toRoutingWaypoints(waypoints);
        if (routingWaypoints.size() < 2) {
            return null;
        }

        RoutingContext routingContext = new RoutingContext();
        routingContext.localFunction = profileFile.getAbsolutePath();
        routingContext.memoryclass = DEFAULT_MEMORY_CLASS_MB;

        RoutingEngine routingEngine = new RoutingEngine(null, null, segmentDir, routingWaypoints, routingContext);
        routingEngine.quite = true;

        try {
            routingEngine.doRun(0L);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "BRouter routing ran out of memory", oom);
            return null;
        } catch (Throwable throwable) {
            Log.e(TAG, "Embedded BRouter route execution failed", throwable);
            return null;
        }

        if (routingEngine.getErrorMessage() != null) {
            Log.w(TAG, "BRouter route failed for profile=" + profileFile.getName()
                    + ", error=" + routingEngine.getErrorMessage());
            return null;
        }

        OsmTrack track = routingEngine.getFoundTrack();
        if (track == null || track.nodes == null || track.nodes.size() < MIN_TRACK_POINTS) {
            return null;
        }

        return new Route(
                Math.max(0.0, track.distance),
                Math.max(0.0, track.getTotalSeconds()),
                toLineStringJson(track),
                toRouteProfile(profileFile.getName()),
                Route.SOURCE_BROUTER);
    }

    private boolean isMapDataValid(@NonNull File mapDataFile) {
        File profilesDir = profilesDir(mapDataFile);
        File segmentDir = segmentDir(mapDataFile);
        File lookups = new File(profilesDir, LOOKUPS_FILE);
        return mapDataFile.exists()
                && mapDataFile.isDirectory()
                && profilesDir.exists()
                && profilesDir.isDirectory()
                && segmentDir.exists()
                && segmentDir.isDirectory()
                && lookups.exists()
                && lookups.isFile()
                && lookups.length() > 0L
                && hasFileWithExtension(profilesDir, ".brf")
                && hasFileWithExtension(segmentDir, ".rd5");
    }

    @Nullable
    private File resolveProfileFile(@NonNull File mapDataFile, @Nullable String requestedProfile) {
        File profilesDir = profilesDir(mapDataFile);
        String resolvedName = resolveProfileFileName(requestedProfile);
        File resolved = new File(profilesDir, resolvedName);
        if (resolved.exists() && resolved.isFile()) {
            return resolved;
        }

        File fallback = new File(profilesDir, DEFAULT_CAR_PROFILE);
        return fallback.exists() && fallback.isFile() ? fallback : null;
    }

    @NonNull
    private List<OsmNodeNamed> toRoutingWaypoints(@NonNull List<Location> waypoints) {
        List<OsmNodeNamed> routingWaypoints = new ArrayList<>();
        for (int index = 0; index < waypoints.size(); index++) {
            Location waypoint = waypoints.get(index);
            if (waypoint == null) {
                continue;
            }

            OsmNodeNamed routingWaypoint = new OsmNodeNamed();
            routingWaypoint.name = waypointName(index, waypoints.size());
            routingWaypoint.ilon = toIntegerLongitude(waypoint.longitude);
            routingWaypoint.ilat = toIntegerLatitude(waypoint.latitude);
            routingWaypoints.add(routingWaypoint);
        }
        return routingWaypoints;
    }

    @NonNull
    private String waypointName(int index, int totalWaypoints) {
        if (index == 0) {
            return "from";
        }
        if (index == totalWaypoints - 1) {
            return "to";
        }
        return "via" + index;
    }

    private int toIntegerLongitude(double longitude) {
        return 180_000_000 + (int) Math.round(longitude * 1_000_000d);
    }

    private int toIntegerLatitude(double latitude) {
        return 90_000_000 + (int) Math.round(latitude * 1_000_000d);
    }

    private boolean hasFileWithExtension(@NonNull File directory, @NonNull String extension) {
        File[] files = directory.listFiles();
        if (files == null) {
            return false;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private File profilesDir(@NonNull File mapDataFile) {
        return new File(mapDataFile, PROFILES_DIR);
    }

    @NonNull
    private File segmentDir(@NonNull File mapDataFile) {
        return new File(mapDataFile, SEGMENTS_DIR);
    }

    @NonNull
    private String resolveProfileFileName(@Nullable String profile) {
        if (profile == null || profile.trim().isEmpty()) {
            return DEFAULT_CAR_PROFILE;
        }

        String normalized = profile.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "car":
            case "driving":
            case "car-fast":
            case "car-fast.brf":
                return DEFAULT_CAR_PROFILE;
            case "bike":
            case "bicycle":
            case "cycling":
            case "fastbike":
            case "fastbike.brf":
            case "bicycle.brf":
                return DEFAULT_BICYCLE_PROFILE;
            case "foot":
            case "walk":
            case "walking":
            case "hiking":
            case "foot.brf":
            case "hiking-mountain.brf":
                return DEFAULT_FOOT_PROFILE;
            default:
                return normalized.endsWith(".brf") ? normalized : DEFAULT_CAR_PROFILE;
        }
    }

    @NonNull
    private String toRouteProfile(@NonNull String resolvedProfileFileName) {
        String normalized = resolvedProfileFileName.toLowerCase(Locale.ROOT);
        if (DEFAULT_CAR_PROFILE.equals(normalized)) {
            return "car";
        }
        if (DEFAULT_BICYCLE_PROFILE.equals(normalized)) {
            return "bike";
        }
        if (DEFAULT_FOOT_PROFILE.equals(normalized)) {
            return "foot";
        }
        return normalized.endsWith(".brf")
                ? normalized.substring(0, normalized.length() - 4)
                : normalized;
    }

    @NonNull
    private String toLineStringJson(@NonNull OsmTrack track) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"LineString\",\"coordinates\":[");
        for (int i = 0; i < track.nodes.size(); i++) {
            OsmPathElement point = track.nodes.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('[')
                    .append(formatLongitude(point.getILon()))
                    .append(',')
                    .append(formatLatitude(point.getILat()))
                    .append(']');
        }
        builder.append("]}");
        return builder.toString();
    }

    private double formatLongitude(int integerLongitude) {
        return (integerLongitude - 180_000_000) / 1_000_000d;
    }

    private double formatLatitude(int integerLatitude) {
        return (integerLatitude - 90_000_000) / 1_000_000d;
    }
}
