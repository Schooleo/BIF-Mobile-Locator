package com.bif.app.data.source;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchByTextRequest;
import com.google.android.libraries.places.api.net.SearchByTextResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class GoogleMapsDataSource {

    private final PlacesClient placesClient;
    private final Geocoder geocoder;

    @Inject
    public GoogleMapsDataSource(@ApplicationContext Context context) {
        placesClient = Places.createClient(context);
        geocoder = new Geocoder(context);
    }

    public Task<SearchByTextResponse> searchPlaces(String query) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.RATING,
                Place.Field.LOCATION
        );

        SearchByTextRequest request = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(5)
                .build();

        return placesClient.searchByText(request);
    }

    public List<Address> geocodeLocation(String query) throws IOException {
        return geocoder.getFromLocationName(query, 1);
    }
}
