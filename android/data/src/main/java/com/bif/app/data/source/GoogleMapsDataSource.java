package com.bif.app.data.source;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FetchPlaceResponse;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse;
import com.google.android.libraries.places.api.net.PlacesClient;

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
        this.placesClient = Places.createClient(context);
        this.geocoder = new Geocoder(context);
    }

    public Task<FindAutocompletePredictionsResponse> getAutocompletePredictions(String query) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build();
        return placesClient.findAutocompletePredictions(request);
    }

    public Task<FetchPlaceResponse> fetchPlaceDetails(String placeId) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.RATING,
                Place.Field.LOCATION
        );

        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);
        return placesClient.fetchPlace(request);
    }

    public List<Address> geocodeLocation(String query) throws IOException {
        return geocoder.getFromLocationName(query, 1);
    }
}