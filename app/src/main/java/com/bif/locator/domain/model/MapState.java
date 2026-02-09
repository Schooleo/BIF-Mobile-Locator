package com.bif.locator.domain.model;

public class MapState {
    public final double latitude;
    public final double longitude;
    public final float zoomLevel;

    public MapState(double latitude, double longitude, float zoomLevel) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoomLevel = zoomLevel;
    }
}
