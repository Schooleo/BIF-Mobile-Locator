package com.bif.locator.data.model;

import com.google.android.gms.maps.model.LatLng;

public class Location {
    private String id;
    private String name;
    private String description;
    private double lat;
    private double lng;
    private String type;

    public Location(String id, String name, String description, double lat, double lng, String type) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.lat = lat;
        this.lng = lng;
        this.type = type;
    }

    // Getters (Required for accessing data)
    public String getName() { return name; }
    public LatLng getLatLng() { return new LatLng(lat, lng); }
    public String getType() { return type; }
}