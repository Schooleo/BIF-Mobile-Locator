package com.bif.app.domain.model;

public class Location {
    public double latitude;
    public double longitude;

    public Location() {
        this.latitude = 0.0;
        this.longitude = 0.0;
    }

    public Location(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;
    }
}
