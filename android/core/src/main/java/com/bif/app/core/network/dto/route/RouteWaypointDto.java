package com.bif.app.core.network.dto.route;

public class RouteWaypointDto {
    public double latitude;
    public double longitude;

    public RouteWaypointDto() {
    }

    public RouteWaypointDto(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

