package com.bif.app.domain.model;

public class Place {
    public String id;
    public String name;
    public String address;
    public double rating;
    public Location location;

    public Place(String id, String name, String address, double rating, Location location) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.location = location;
    }
}
