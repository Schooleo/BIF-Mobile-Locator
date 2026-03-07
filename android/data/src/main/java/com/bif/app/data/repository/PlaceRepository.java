package com.bif.app.data.repository;

import android.location.Address;
import android.util.Log;

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

        // --- Mock Search --- //
        executorService.execute(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Log.e("PlaceRepository", "Search interrupted: ", e);
            }

            String lowerQuery = query.toLowerCase();
            List<Place> allMocks = getMockPlaces();
            List<Place> filteredResults = new ArrayList<>();

            for (Place place : allMocks) {
                if (place.name.toLowerCase().contains(lowerQuery) ||
                        place.address.toLowerCase().contains(lowerQuery)) {
                    filteredResults.add(place);
                }
            }

            result.postValue(filteredResults);
        });
        return result;
        // ------------------- //

//        googleMapsDataSource.getAutocompletePredictions(query)
//                .addOnSuccessListener(response -> {
//                    List<Place> domainPlaces = new ArrayList<>();
//                    int totalPredictions = response.getAutocompletePredictions().size();
//
//                    if (totalPredictions == 0) {
//                        result.postValue(domainPlaces);
//                        return;
//                    }
//
//                    int limit = Math.min(totalPredictions, 5);
//                    int[] completedCount = {0};
//
//                    for (int i = 0; i < limit; i++) {
//                        String placeId = response.getAutocompletePredictions().get(i).getPlaceId();
//
//                        googleMapsDataSource.fetchPlaceDetails(placeId)
//                                .addOnSuccessListener(placeResponse -> {
//                                    com.google.android.libraries.places.api.model.Place googlePlace = placeResponse.getPlace();
//
//                                    Location domainLocation = new Location();
//                                    if (googlePlace.getLocation() != null) {
//                                        domainLocation.latitude = googlePlace.getLocation().latitude;
//                                        domainLocation.longitude = googlePlace.getLocation().longitude;
//                                    }
//
//                                    Place domainPlace = new Place(
//                                            googlePlace.getId(),
//                                            googlePlace.getDisplayName() != null ? googlePlace.getDisplayName() : "",
//                                            googlePlace.getFormattedAddress() != null ? googlePlace.getFormattedAddress() : "",
//                                            googlePlace.getRating() != null ? googlePlace.getRating() : 0.0,
//                                            domainLocation
//                                    );
//
//                                    synchronized (domainPlaces) {
//                                        domainPlaces.add(domainPlace);
//                                        completedCount[0]++;
//                                        if (completedCount[0] >= limit) {
//                                            result.postValue(new ArrayList<>(domainPlaces));
//                                        }
//                                    }
//                                })
//                                .addOnFailureListener(e -> {
//                                    synchronized (domainPlaces) {
//                                        completedCount[0]++;
//                                        if (completedCount[0] >= limit) {
//                                            result.postValue(new ArrayList<>(domainPlaces));
//                                        }
//                                    }
//                                });
//                    }
//                })
//                .addOnFailureListener(e -> {
//                    Log.e("PlaceRepository", "Search failed: ", e);
//
//                    result.postValue(new ArrayList<>());
//                });
//
//        return result;
    }

    private List<Place> getMockPlaces() {
        List<Place> mocks = new ArrayList<>();

        // HCM Universities
        mocks.add(new Place(
                "mock_1", "University of Science",
                "227 Nguyễn Văn Cừ, Phường 4, Quận 5, Thành phố Hồ Chí Minh",
                4.8, new Location(10.762841085372505, 106.6824858211968)));

        mocks.add(new Place(
                "mock_2", "University of Technology",
                "268 Đ. Lý Thường Kiệt, Phường 14, Quận 10, Thành phố Hồ Chí Minh",
                4.5, new Location(10.772212006405189, 106.65789106669027)));

        mocks.add(new Place(
                "mock_3", "Ton Duc Thang University",
                "19 Nguyễn Hữu Thọ, Phường Tân Hưng, Quận 7, Thành phố Hồ Chí Minh",
                4.9, new Location(10.731929429867792, 106.69932321309254)));

        mocks.add(new Place(
                "mock_4", "University of Economics",
                "279 Nguyễn Tri Phương, Phường 8, Quận 10, Thành phố Hồ Chí Minh",
                3.8, new Location(10.761075357981296, 106.6683613353801)));

        mocks.add(new Place(
                "mock_5", "University of Social Sciences and Humanities",
                "10-12 Đinh Tiên Hoàng, Phường Sài Gòn, Quận 1, Thành phố Hồ Chí Minh",
                5.0, new Location(10.785913385786856, 106.70268713785481)));

        return mocks;
    }
}
