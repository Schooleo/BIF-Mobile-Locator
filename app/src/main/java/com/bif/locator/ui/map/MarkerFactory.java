package com.bif.locator.ui.map;

import android.content.Context;

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

    public static MarkerOptions createMarker(LatLng position) {
        return createMarker(position, null, null);
    }

    public static MarkerOptions createMarker(LatLng position, String title, String snippet) {
        if (position == null) {
            throw new IllegalArgumentException("Position cannot be null");
        }

        if (title == null || title.isEmpty()) {
            title = "Location";
        }

        if (snippet == null || snippet.isEmpty()) {
            snippet = "Selected Marker Location";
        }

        return new MarkerOptions()
                .position(position)
                .title(title)
                .snippet(snippet)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
    }
}
