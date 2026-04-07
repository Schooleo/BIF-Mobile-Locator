package com.bif.app.domain.model;

public class AiTripDraftStop {
    private final String placeId;
    private final Place place;
    private final int durationMinutes;
    private final String note;

    public AiTripDraftStop(String placeId, Place place, int durationMinutes, String note) {
        this.placeId = placeId;
        this.place = place;
        this.durationMinutes = durationMinutes;
        this.note = note;
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
}
