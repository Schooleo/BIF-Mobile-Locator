package com.bif.app.core.network.dto.trip;

import java.util.List;

public class TripPlanDto {
    public String id;
    public String groupId;
    public String title;
    public String description;
    public String startAt;
    public String endAt;
    public long serverVersion;
    public boolean deleted;
    public List<TripStopDto> stops;
    public List<String> participantIds;
}

