package com.bif.app.core.network.dto.place;

public class PlaceSearchRequestDTO {
    public String query;
    public Double latitude;
    public Double longitude;
    public Integer perPage;

    public PlaceSearchRequestDTO() {
    }

    public PlaceSearchRequestDTO(String query, Double latitude,
                                 Double longitude, Integer perPage) {
        this.query = query;
        this.latitude = latitude;
        this.longitude = longitude;
        this.perPage = perPage;
    }
}
