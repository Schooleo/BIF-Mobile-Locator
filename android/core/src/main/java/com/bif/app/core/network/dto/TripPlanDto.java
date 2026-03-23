package com.bif.app.core.network.dto;

import java.util.List;

public class TripPlanDto {
    public String id;
    public String groupId;
    public String title;
    public String description;
    public String startAt;
    public String endAt;
    public List<TripStopDto> stops;
    public List<String> participantIds;
}
