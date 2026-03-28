package com.bif.server.features.trip.models;

import com.bif.server.common.models.Location;
import lombok.Data;

import java.time.Instant;

import java.util.UUID;

@Data
public class TripStop {
    private String id = UUID.randomUUID().toString();
    private String title;
    private String note;
    private Location location;
    private Instant arrivalTime;
    private Instant departureTime;
    private int orderIndex;
}