package com.bif.app.data.repository;

import android.location.Address;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.repository.IPlaceRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class PlaceRepository implements IPlaceRepository {

    private final GoogleMapsDataSource googleMapsDataSource;
    private final ExecutorService executorService;

    @Inject
    public PlaceRepository(GoogleMapsDataSource googleMapsDataSource) {
        this.googleMapsDataSource = googleMapsDataSource;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<Location> searchLocation(String query) {
        MutableLiveData<Location> result = new MutableLiveData<>();
        executorService.execute(() -> {
            try {
                List<Address> results = googleMapsDataSource.geocodeLocation(query);
                if (results != null && !results.isEmpty()) {
                    Address address = results.get(0);
                    Location location = new Location();
                    location.latitude = address.getLatitude();
                    location.longitude = address.getLongitude();
                    result.postValue(location);
                } else {
                    result.postValue(null);
                }
            } catch (IOException e) {
                result.postValue(null);
            }
        });
        return result;
    }

    @Override
    public LiveData<List<Place>> searchPlaces(String query) {
        MutableLiveData<List<Place>> result = new MutableLiveData<>();

        if (query == null || query.isEmpty()) {
            result.postValue(new ArrayList<>());
            return result;
        }

        googleMapsDataSource.searchPlaces(query)
                .addOnSuccessListener(response -> {
                    List<Place> domainPlaces = new ArrayList<>();

                    for (com.google.android.libraries.places.api.model.Place googlePlace : response.getPlaces()) {
                        double lat = 0, lng = 0;
                        if (googlePlace.getLocation() != null) {
                            lat = googlePlace.getLocation().latitude;
                            lng = googlePlace.getLocation().longitude;
                        }

                        Place domainPlace = new Place(
                                googlePlace.getId(),
                                googlePlace.getDisplayName() != null ? googlePlace.getDisplayName() : "",
                                googlePlace.getFormattedAddress() != null ? googlePlace.getFormattedAddress() : "",
                                googlePlace.getRating() != null ? googlePlace.getRating() : 0.0,
                                lat,
                                lng
                        );
                        domainPlaces.add(domainPlace);
                    }
                    result.postValue(domainPlaces);
                })
                .addOnFailureListener(e -> result.postValue(new ArrayList<>()));

        return result;
    }
}
