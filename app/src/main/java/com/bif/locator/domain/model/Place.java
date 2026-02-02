package com.bif.locator.domain.model;

public class Place {
    public String id;
    public String name;
    public String address;
    public double rating;
    public double latitude;
    public double longitude;

    public Place(String id, String name, String address, double rating, double latitude, double longitude) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
