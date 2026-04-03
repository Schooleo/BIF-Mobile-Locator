package com.bif.app.feature.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bif.app.domain.model.Location;

import org.junit.Test;
import org.maplibre.geojson.Point;

import java.util.List;

public class RouteGeometryUtilsTest {

    @Test
    public void computeRouteProgress_splitsPassedAndRemainingRouteNearUser() {
        List<Point> routePoints = List.of(
                Point.fromLngLat(106.0000, 10.0000),
                Point.fromLngLat(106.0100, 10.0000),
                Point.fromLngLat(106.0200, 10.0000));

        RouteGeometryUtils.RouteProgress progress = RouteGeometryUtils.computeRouteProgress(
                routePoints,
                new Location(10.0000, 106.0125));

        assertNotNull(progress.snappedPoint);
        assertTrue(progress.passedPoints.size() >= 2);
        assertTrue(progress.remainingPoints.size() >= 2);
        assertEquals(106.0125, progress.snappedPoint.longitude(), 0.0015);
        assertEquals(10.0000, progress.snappedPoint.latitude(), 0.0005);
    }

    @Test
    public void extractTurnMarkers_detectsSignificantCorner() {
        List<Point> routePoints = List.of(
                Point.fromLngLat(106.0000, 10.0000),
                Point.fromLngLat(106.0100, 10.0000),
                Point.fromLngLat(106.0100, 10.0100),
                Point.fromLngLat(106.0100, 10.0200));

        List<RouteGeometryUtils.TurnMarker> markers = RouteGeometryUtils.extractTurnMarkers(routePoints);

        assertFalse(markers.isEmpty());
        RouteGeometryUtils.TurnMarker firstMarker = markers.get(0);
        assertEquals(106.0100, firstMarker.location.longitude(), 0.0005);
        assertEquals(10.0000, firstMarker.location.latitude(), 0.0005);
    }

    @Test
    public void bearingDegrees_returnsExpectedHeadingForNorthboundSegment() {
        float bearing = RouteGeometryUtils.bearingDegrees(
                Point.fromLngLat(106.0000, 10.0000),
                Point.fromLngLat(106.0000, 10.0100));

        assertEquals(0f, bearing, 3f);
    }
}
