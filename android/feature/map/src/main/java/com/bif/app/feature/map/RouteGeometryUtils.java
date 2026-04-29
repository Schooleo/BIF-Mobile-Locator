package com.bif.app.feature.map;

import androidx.annotation.NonNull;

import com.bif.app.domain.model.Location;

import org.maplibre.geojson.Point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RouteGeometryUtils {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final double MIN_TURN_SEGMENT_METERS = 18d;
    private static final double MIN_TURN_ANGLE_DEGREES = 28d;

    private RouteGeometryUtils() {
    }

    static final class RouteProgress {
        final List<Point> passedPoints;
        final List<Point> remainingPoints;
        final Point snappedPoint;
        final float segmentBearing;

        RouteProgress(@NonNull List<Point> passedPoints,
                      @NonNull List<Point> remainingPoints,
                      @NonNull Point snappedPoint,
                      float segmentBearing) {
            this.passedPoints = passedPoints;
            this.remainingPoints = remainingPoints;
            this.snappedPoint = snappedPoint;
            this.segmentBearing = segmentBearing;
        }
    }

    static final class TurnMarker {
        final Point location;
        final float bearing;

        TurnMarker(@NonNull Point location, float bearing) {
            this.location = location;
            this.bearing = bearing;
        }
    }

    @NonNull
    static RouteProgress computeRouteProgress(@NonNull List<Point> routePoints,
                                              @NonNull Location userLocation) {
        if (routePoints.size() < 2) {
            Point fallback = routePoints.isEmpty()
                    ? Point.fromLngLat(userLocation.longitude, userLocation.latitude)
                    : routePoints.get(0);
            return new RouteProgress(
                    Collections.singletonList(fallback),
                    Collections.singletonList(fallback),
                    fallback,
                    0f);
        }

        double refLat = userLocation.latitude;
        double bestDistanceSq = Double.MAX_VALUE;
        int bestSegmentIndex = 0;
        double bestFraction = 0d;
        Point bestProjection = routePoints.get(0);

        for (int i = 0; i < routePoints.size() - 1; i++) {
            Point start = routePoints.get(i);
            Point end = routePoints.get(i + 1);
            ProjectionResult projection = projectOntoSegment(start, end, userLocation, refLat);
            if (projection.distanceSquared < bestDistanceSq) {
                bestDistanceSq = projection.distanceSquared;
                bestSegmentIndex = i;
                bestFraction = projection.fraction;
                bestProjection = projection.projectedPoint;
            }
        }

        List<Point> passedPoints = new ArrayList<>();
        for (int i = 0; i <= bestSegmentIndex; i++) {
            passedPoints.add(routePoints.get(i));
        }
        appendIfDistinct(passedPoints, bestProjection);

        List<Point> remainingPoints = new ArrayList<>();
        remainingPoints.add(bestProjection);
        for (int i = bestSegmentIndex + 1; i < routePoints.size(); i++) {
            appendIfDistinct(remainingPoints, routePoints.get(i));
        }

        float segmentBearing = bearingDegrees(routePoints.get(bestSegmentIndex), routePoints.get(bestSegmentIndex + 1));
        if (bestFraction >= 1d && bestSegmentIndex + 2 < routePoints.size()) {
            segmentBearing = bearingDegrees(routePoints.get(bestSegmentIndex + 1), routePoints.get(bestSegmentIndex + 2));
        }

        return new RouteProgress(passedPoints, remainingPoints, bestProjection, segmentBearing);
    }

    @NonNull
    static List<TurnMarker> extractTurnMarkers(@NonNull List<Point> routePoints) {
        if (routePoints.size() < 3) {
            return Collections.emptyList();
        }

        List<TurnMarker> markers = new ArrayList<>();
        for (int i = 1; i < routePoints.size() - 1; i++) {
            Point previous = routePoints.get(i - 1);
            Point current = routePoints.get(i);
            Point next = routePoints.get(i + 1);

            double incomingDistance = distanceMeters(previous, current);
            double outgoingDistance = distanceMeters(current, next);
            if (incomingDistance < MIN_TURN_SEGMENT_METERS || outgoingDistance < MIN_TURN_SEGMENT_METERS) {
                continue;
            }

            float incomingBearing = bearingDegrees(previous, current);
            float outgoingBearing = bearingDegrees(current, next);
            double delta = normalizeDelta(outgoingBearing - incomingBearing);
            if (Math.abs(delta) < MIN_TURN_ANGLE_DEGREES) {
                continue;
            }

            markers.add(new TurnMarker(current, outgoingBearing));
        }
        return markers;
    }

    private static void appendIfDistinct(@NonNull List<Point> points, @NonNull Point candidate) {
        if (points.isEmpty()) {
            points.add(candidate);
            return;
        }

        Point last = points.get(points.size() - 1);
        if (Math.abs(last.longitude() - candidate.longitude()) > 1e-9
                || Math.abs(last.latitude() - candidate.latitude()) > 1e-9) {
            points.add(candidate);
        }
    }

    @NonNull
    private static ProjectionResult projectOntoSegment(@NonNull Point start,
                                                       @NonNull Point end,
                                                       @NonNull Location userLocation,
                                                       double referenceLatitude) {
        XY startXY = toMeters(start.longitude(), start.latitude(), referenceLatitude);
        XY endXY = toMeters(end.longitude(), end.latitude(), referenceLatitude);
        XY userXY = toMeters(userLocation.longitude, userLocation.latitude, referenceLatitude);

        double segmentX = endXY.x - startXY.x;
        double segmentY = endXY.y - startXY.y;
        double segmentLengthSq = segmentX * segmentX + segmentY * segmentY;
        if (segmentLengthSq <= 0d) {
            return new ProjectionResult(start, 0d, distanceSquared(startXY, userXY));
        }

        double fraction = ((userXY.x - startXY.x) * segmentX + (userXY.y - startXY.y) * segmentY)
                / segmentLengthSq;
        fraction = clamp(fraction, 0d, 1d);

        double projectedX = startXY.x + fraction * segmentX;
        double projectedY = startXY.y + fraction * segmentY;
        Point projectedPoint = toPoint(projectedX, projectedY, referenceLatitude);

        double dx = userXY.x - projectedX;
        double dy = userXY.y - projectedY;
        return new ProjectionResult(projectedPoint, fraction, dx * dx + dy * dy);
    }

    private static double distanceSquared(@NonNull XY first, @NonNull XY second) {
        double dx = first.x - second.x;
        double dy = first.y - second.y;
        return dx * dx + dy * dy;
    }

    @NonNull
    private static XY toMeters(double longitude, double latitude, double referenceLatitude) {
        double latRadians = Math.toRadians(referenceLatitude);
        double x = Math.toRadians(longitude) * EARTH_RADIUS_METERS * Math.cos(latRadians);
        double y = Math.toRadians(latitude) * EARTH_RADIUS_METERS;
        return new XY(x, y);
    }

    @NonNull
    private static Point toPoint(double xMeters, double yMeters, double referenceLatitude) {
        double latRadians = yMeters / EARTH_RADIUS_METERS;
        double lonRadians = xMeters / (EARTH_RADIUS_METERS * Math.cos(Math.toRadians(referenceLatitude)));
        return Point.fromLngLat(Math.toDegrees(lonRadians), Math.toDegrees(latRadians));
    }

    static float bearingDegrees(@NonNull Point from, @NonNull Point to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double y = Math.sin(deltaLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (float) ((bearing + 360d) % 360d);
    }

    static double distanceMeters(@NonNull Point from, @NonNull Point to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(deltaLat / 2d) * Math.sin(deltaLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2d) * Math.sin(deltaLon / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }

    static double calculateDistance(@NonNull Location from, @NonNull Location to) {
        return distanceMeters(
                Point.fromLngLat(from.longitude, from.latitude),
                Point.fromLngLat(to.longitude, to.latitude));
    }

    static double polylineDistanceMeters(@NonNull List<Point> points) {
        if (points.size() < 2) {
            return 0d;
        }
        double distance = 0d;
        for (int i = 0; i < points.size() - 1; i++) {
            distance += distanceMeters(points.get(i), points.get(i + 1));
        }
        return distance;
    }

    private static double normalizeDelta(double delta) {
        while (delta > 180d) {
            delta -= 360d;
        }
        while (delta < -180d) {
            delta += 360d;
        }
        return delta;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class XY {
        final double x;
        final double y;

        XY(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class ProjectionResult {
        final Point projectedPoint;
        final double fraction;
        final double distanceSquared;

        ProjectionResult(@NonNull Point projectedPoint,
                         double fraction,
                         double distanceSquared) {
            this.projectedPoint = projectedPoint;
            this.fraction = fraction;
            this.distanceSquared = distanceSquared;
        }
    }
}
