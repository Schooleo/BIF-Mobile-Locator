package com.bif.app.domain.model;

public class AiTripDraftStop {
    private final String placeId;
    private final Place place;
    private final int durationMinutes;
    private final String note;
    private final String plannedDateTime;

    public AiTripDraftStop(String placeId,
                           Place place,
                           int durationMinutes,
                           String note,
                           String plannedDateTime) {
        if (durationMinutes < 0) {
            throw new IllegalArgumentException("durationMinutes must be >= 0");
        }
        this.placeId = placeId;
        this.place = place;
        this.durationMinutes = durationMinutes;
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

    public String getNote() {
        return note;
    }

    public String getPlannedDateTime() {
        return plannedDateTime;
    }
}
