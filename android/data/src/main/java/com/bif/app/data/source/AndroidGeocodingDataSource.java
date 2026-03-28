package com.bif.app.data.source;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class AndroidGeocodingDataSource {

    private final Geocoder geocoder;

    @Inject
    public AndroidGeocodingDataSource(@ApplicationContext Context context) {
        this.geocoder = new Geocoder(context);
    }

    public List<Address> geocodeLocation(String query) throws IOException {
        return geocoder.getFromLocationName(query, 1);
    }
}
