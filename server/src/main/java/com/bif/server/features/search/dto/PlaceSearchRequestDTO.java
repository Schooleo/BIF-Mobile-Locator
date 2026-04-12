package com.bif.server.features.search.dto;

public class PlaceSearchRequestDTO {

    private static final int DEFAULT_PER_PAGE = 15;
    private static final int MAX_PER_PAGE = 50;

    private String query;
    private Double latitude;
    private Double longitude;
    private Integer perPage = DEFAULT_PER_PAGE;

    public PlaceSearchRequestDTO() {
    }

    public PlaceSearchRequestDTO(String query, Double latitude, Double longitude,
                                 Integer perPage) {
        this.query = query;
        this.latitude = latitude;
        this.longitude = longitude;
        this.perPage = perPage;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getPerPage() {
        if (perPage == null || perPage <= 0) {
            return DEFAULT_PER_PAGE;
        }
        return Math.min(perPage, MAX_PER_PAGE);
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }
}
