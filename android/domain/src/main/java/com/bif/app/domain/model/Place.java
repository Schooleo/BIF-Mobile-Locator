package com.bif.app.domain.model;

public class Place {
    public String id;
    public String name;
    public String address;
    public double rating;
    public Location location;
    public String placeSource; // e.g., "OSM", "Typesense"

    public Place(String id, String name, String address, double rating, Location location) {
        this(id, name, address, rating, location, "OSM");
    }

    public Place(String id, String name, String address, double rating, Location location, String placeSource) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.location = location;
        this.placeSource = placeSource;
    }
}
