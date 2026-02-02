package com.bif.locator.domain.model;

public class MapState {
    public double latitude;
    public double longitude;
    public float zoomLevel;

    public MapState(double latitude, double longitude, float zoomLevel) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoomLevel = zoomLevel;
    }
}
