package com.bif.server.features.place.models;

import lombok.Data;

import java.time.Instant;

@Data
public class PlaceReview {
    private String userId;
    private String userName;
    private int rating;
    private String comment;
    private Instant createdAt;
}
