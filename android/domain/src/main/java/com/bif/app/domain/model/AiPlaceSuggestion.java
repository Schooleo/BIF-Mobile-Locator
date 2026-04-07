package com.bif.app.domain.model;

public class AiPlaceSuggestion {
    private final Place place;
    private final int addedToTripCount;

    public AiPlaceSuggestion(Place place, int addedToTripCount) {
        this.place = place;
        this.addedToTripCount = addedToTripCount;
    }

    public Place getPlace() {
        return place;
    }

    public int getAddedToTripCount() {
        return addedToTripCount;
    }
}
