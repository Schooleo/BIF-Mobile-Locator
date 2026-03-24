package com.bif.app.core.network.dto;

import java.util.List;

public class PlaceDto {
    public String id;
    public String name;
    public String address;
    public double rating;
    public double latitude;
    public double longitude;
    public List<String> tags;
    public String placeSource;
    public String persistedByAction;
    public String persistedByUserId;
    public int reviewCount;
    public long serverVersion;
    public boolean deleted;
}
