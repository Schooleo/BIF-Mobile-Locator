package com.bif.server.features.trip.models;

import lombok.Data;

@Data
public class RearrangeStopInput {
    private String id;
    private int orderIndex;
}
