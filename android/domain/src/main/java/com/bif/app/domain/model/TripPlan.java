package com.bif.app.domain.model;

import java.util.List;

public class TripPlan {
    private final String id;
    private final String groupId;
    private String title;
    private String description;
    private final long startAt;
    private final long endAt;
    private List<TripStop> stops;
    private List<String> participantIds;

    public TripPlan(String id, String groupId, String title, String description,
                    long startAt, long endAt, List<TripStop> stops,
                    List<String> participantIds) {
        this.id = id;
        this.groupId = groupId;
        this.title = title;
        this.description = description;
        this.startAt = startAt;
        this.endAt = endAt;
        this.stops = stops;
        this.participantIds = participantIds;
    }

    public String getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public long getStartAt() { return startAt; }
    public long getEndAt() { return endAt; }
    public List<TripStop> getStops() { return stops; }
    public List<String> getParticipantIds() { return participantIds; }

    public int getStopCount() {
        return stops != null ? stops.size() : 0;
    }
}
