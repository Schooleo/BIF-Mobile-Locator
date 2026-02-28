package com.bif.app.feature.map;

import android.content.Context;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import javax.inject.Inject;
import dagger.hilt.android.qualifiers.ApplicationContext;

public class MarkerFactory {
    private final Context context;

    @Inject
    public MarkerFactory(@ApplicationContext Context context) {
        this.context = context;
    }

    public enum MarkerType {
        CURRENT_LOCATION,  // Blue marker for user's current location
        PLACE_LOCATION     // Green marker for searched/selected places
    }

    public static MarkerOptions createMarker(LatLng position) {
        return createMarker(position, null, null, MarkerType.PLACE_LOCATION);
    }

    public static MarkerOptions createMarker(LatLng position, String title, String snippet) {
        return createMarker(position, title, snippet, MarkerType.PLACE_LOCATION);
    }

    public static MarkerOptions createMarker(LatLng position, String title, String snippet, MarkerType type) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        if (title == null || title.isEmpty()) {
            title = type == MarkerType.CURRENT_LOCATION ? "Your Location" : "Location";
        }

        if (snippet == null || snippet.isEmpty()) {
            snippet = type == MarkerType.CURRENT_LOCATION ? "You are here" : "Selected Marker Location";
        }

        // Use vector drawable for custom marker icons
        BitmapDescriptor icon;
        if (type == MarkerType.CURRENT_LOCATION) {
            // Blue marker for current location
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        } else {
            // Green marker for places
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        }

        return new MarkerOptions()
                .position(position)
                .title(title)
                .snippet(snippet)
                .icon(icon);
    }

    /**
     * Create a marker for the user's current location (blue)
     */
    public static MarkerOptions createCurrentLocationMarker(LatLng position) {
        return createMarker(position, "Your Location", "You are here", MarkerType.CURRENT_LOCATION);
    }

    /**
     * Create a marker for a place/search result (green)
     */
    public static MarkerOptions createPlaceMarker(LatLng position, String title) {
        return createMarker(position, title, null, MarkerType.PLACE_LOCATION);
    }
}
