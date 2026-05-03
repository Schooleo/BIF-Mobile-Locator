package com.bif.app.domain.model;

public class AiTripDraftStop {
    private final String placeId;
    private final Place place;
    private final int durationMinutes;
    private final String startTime;
    private final String endTime;
    private final Integer duration;
    private final String note;
    private final String plannedDateTime;

    public AiTripDraftStop(String placeId,
                           Place place,
                           int durationMinutes,
                           String note,
                           String plannedDateTime) {
        this(placeId, place, durationMinutes, null, null, durationMinutes, note, plannedDateTime);
    }

    public AiTripDraftStop(String placeId,
                           Place place,
                           int durationMinutes,
                           String startTime,
                           String endTime,
                           Integer duration,
                           String note,
                           String plannedDateTime) {
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must be >= 0");
        }
        this.placeId = placeId;
        this.place = place;
        this.durationMinutes = durationMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.duration = duration;
        this.note = note;
        this.plannedDateTime = plannedDateTime;
    }

    public String getPlaceId() {
        return placeId;
    }

    public Place getPlace() {
        return place;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public String getNote() {
        return note;
    }

    public String getPlannedDateTime() {
        return plannedDateTime;
    }
}
