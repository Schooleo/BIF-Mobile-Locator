package com.bif.app.domain.model;

public class Place {
    public static final String SOURCE_OSM = "OSM";
    public static final String SOURCE_PREVIEW = "PREVIEW";

    public enum SelectionState {
        CANONICAL,
        PREVIEW
    }

    public String id;
    public String name;
    public String address;
    public double rating;
    public Location location;
    public String placeSource; // e.g., "OSM", "Typesense"
    public SelectionState selectionState;

    public Place(String id, String name, String address, double rating, Location location) {
        this(id, name, address, rating, location, SOURCE_OSM, SelectionState.CANONICAL);
    }

    public Place(String id, String name, String address, double rating, Location location, String placeSource) {
        this(id, name, address, rating, location, placeSource, SelectionState.CANONICAL);
    }

    public Place(String id,
                 String name,
                 String address,
                 double rating,
                 Location location,
                 String placeSource,
                 SelectionState selectionState) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.location = location;
        this.placeSource = placeSource;
        this.selectionState = selectionState != null ? selectionState : SelectionState.CANONICAL;
    }

    public boolean isPreviewSelection() {
        return selectionState == SelectionState.PREVIEW;
    }

    public boolean canResolveCanonicalIdentity() {
        return !isPreviewSelection()
                && hasText(placeSource)
                && hasText(id)
                && hasText(name)
                && location != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}
