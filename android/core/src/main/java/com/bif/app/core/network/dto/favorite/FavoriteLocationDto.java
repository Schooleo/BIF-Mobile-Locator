package com.bif.app.core.network.dto.favorite;

public class FavoriteLocationDto {
    public double latitude;
    public double longitude;

    public FavoriteLocationDto() {
    }

    public FavoriteLocationDto(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
