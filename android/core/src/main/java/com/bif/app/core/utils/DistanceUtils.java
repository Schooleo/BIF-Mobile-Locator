package com.bif.app.core.utils;

import java.util.List;

public class DistanceUtils {

    private static final int EARTH_RADIUS_KM = 6371;

    public static final class GeoPoint {
        public final double latitude;
        public final double longitude;

        public GeoPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * Calculates the distance between two points using the Haversine formula.
     *
     * @param lat1 Latitude of the first point
     * @param lon1 Longitude of the first point
     * @param lat2 Latitude of the second point
     * @param lon2 Longitude of the second point
     * @return Distance in Kilometers
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Calculates the total straight-line distance for consecutive waypoints.
     * This is intended for pre-routing heuristics, not final route distance display.
     */
    public static double calculateTotalDistanceKm(List<GeoPoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return 0.0;
        }

        double total = 0.0;
        for (int i = 0; i < waypoints.size() - 1; i++) {
            GeoPoint from = waypoints.get(i);
            GeoPoint to = waypoints.get(i + 1);
            if (from == null || to == null) {
                continue;
            }
            total += calculateDistance(
                    from.latitude, from.longitude,
                    to.latitude, to.longitude
            );
        }

        return total;
    }

    /**
     * Chooses a routing profile based on pre-routing straight-line distance.
     */
    public static String determineProfile(double totalDistanceKm) {
        double safeDistance = Math.max(0.0, totalDistanceKm);
        if (safeDistance < 3.0) {
            return "foot";
        }
        if (safeDistance <= 15.0) {
            return "bike";
        }
        return "car";
    }
}
