package com.bif.app.domain.model;

public class Route {

    public static final String SOURCE_ONLINE = "ONLINE";
    public static final String SOURCE_OFFLINE = "OFFLINE";
    public static final String SOURCE_GRAPHHOPPER = "GRAPHHOPPER";

    private final double distanceMeters;
    private final double durationSeconds;
    private final String geometryJson;
    private final String profile;
    private final String source;

    public Route(double distanceMeters,
                 double durationSeconds,
                 String geometryJson,
                 String profile,
                 String source) {
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.geometryJson = geometryJson;
        this.profile = profile;
        this.source = source;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public double getDurationSeconds() {
        return durationSeconds;
    }

    public String getGeometryJson() {
        return geometryJson;
    }

    public String getProfile() {
        return profile;
    }

    public String getSource() {
        return source;
    }
}
