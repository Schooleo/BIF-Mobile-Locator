package com.bif.app.domain.model;

import java.util.Objects;

public class MapState {
    public final double latitude;
    public final double longitude;
    public final float zoomLevel;

    public MapState(double latitude, double longitude, float zoomLevel) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoomLevel = zoomLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapState mapState = (MapState) o;
        return Double.compare(mapState.latitude, latitude) == 0 &&
                Double.compare(mapState.longitude, longitude) == 0 &&
                Float.compare(mapState.zoomLevel, zoomLevel) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude, zoomLevel);
    }
}
