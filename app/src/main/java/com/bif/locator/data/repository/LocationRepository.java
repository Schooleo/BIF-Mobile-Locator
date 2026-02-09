package com.bif.locator.data.repository;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Looper;

import com.bif.locator.domain.model.Location;
import com.bif.locator.domain.model.Place;
import com.bif.locator.domain.repository.ILocationRepository;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place.Field;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;

public class LocationRepository implements ILocationRepository {

    private final Context context;
    private final ExecutorService executorService;
    private final FusedLocationProviderClient fusedLocationClient;
    private CancellationTokenSource cancellationTokenSource;
    private final PlacesClient placesClient;
    private com.google.android.gms.location.LocationCallback fusedCallback;

    @Inject
    public LocationRepository(@ApplicationContext Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.placesClient = Places.createClient(context);
    }

    LocationRepository(Context context,
                       FusedLocationProviderClient fusedClient,
                       PlacesClient placesClient,
                       ExecutorService executor) {
        this.context = context;
        this.fusedLocationClient = fusedClient;
        this.placesClient = placesClient;
        this.executorService = executor;
    }

    @SuppressLint("MissingPermission")
    @Override
    public LiveData<Location> getCurrentLocation() {
        MutableLiveData<Location> result = new MutableLiveData<>();

        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
        }
        cancellationTokenSource = new CancellationTokenSource();

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Location currentLocation = new Location();
                        currentLocation.latitude = location.getLatitude();
                        currentLocation.longitude = location.getLongitude();

                        result.postValue(currentLocation);
                    } else {
                        result.postValue(null);
                    }
                })
                .addOnFailureListener(e -> {
                    result.postValue(null);
                });
        return result;
    }

    @Override
    public LiveData<Location> searchLocation(String query) {
        MutableLiveData<Location> result = new MutableLiveData<>();
        
        executorService.execute(() -> {
            Geocoder geocoder = new Geocoder(context);
            try {
                List<Address> results = geocoder.getFromLocationName(query, 1);
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

    @SuppressLint("MissingPermission")
    @Override
    public void requestLocationUpdates(com.bif.locator.domain.repository.LocationCallback callback) {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10000
        )
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                android.location.Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null){
                    Location domainLocation = new Location();
                    domainLocation.latitude = lastLocation.getLatitude();
                    domainLocation.longitude = lastLocation.getLongitude();

                    callback.onLocationResult(domainLocation);
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(
                locationRequest,
                fusedCallback,
                Looper.getMainLooper()
        );
    }

    @Override
    public void removeLocationUpdates(com.bif.locator.domain.repository.LocationCallback callback) {
        if (fusedCallback != null) {
            fusedLocationClient.removeLocationUpdates(fusedCallback);
            fusedCallback = null;
        }

        if (cancellationTokenSource != null){
            cancellationTokenSource.cancel();
            cancellationTokenSource = null;
        }
    }

    @Override
    public LiveData<List<com.bif.locator.domain.model.Place>> searchPlaces(String query) {
        MutableLiveData<List<Place>> result = new MutableLiveData<>();

        if (query == null || query.isEmpty()) {
            result.postValue(new ArrayList<>());
            return result;
        }

        List<Field> placeFields = Arrays.asList(
                Field.ID,
                Field.DISPLAY_NAME,
                Field.FORMATTED_ADDRESS,
                Field.RATING,
                Field.LOCATION
        );

        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    List<Place> places = new ArrayList<>();
                    int totalPredictions = response.getAutocompletePredictions().size();
                    
                    if (totalPredictions == 0){
                        result.postValue(places);
                        return;
                    }

                    int limit = Math.min(totalPredictions, 5);
                    int [] completedCount = {0};

                    for (int i = 0; i < limit; i++) {
                        String placeId = response.getAutocompletePredictions().get(i).getPlaceId();
                        FetchPlaceRequest fetchRequest = FetchPlaceRequest
                                .newInstance(placeId, placeFields);
                        placesClient.fetchPlace(fetchRequest)
                                .addOnSuccessListener(placeResponse -> {
                                    com.google.android.libraries.places.api.model.Place googlePlace =
                                            placeResponse.getPlace();

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

                                    synchronized (places) {
                                        places.add(domainPlace);
                                        completedCount[0]++;
                                        if (completedCount[0] >= limit) {
                                            result.postValue(new ArrayList<>(places));
                                            }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    synchronized (places) {
                                        completedCount[0]++;
                                        if (completedCount[0] >= limit) {
                                            result.postValue(new ArrayList<>(places));
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    result.postValue(new ArrayList<>());
                });

        return result;
    }

    private String formatAddress(Address address){
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i <= address.getMaxAddressLineIndex(); i++){
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append(address.getAddressLine(i));
        }
        return stringBuilder.toString();
    }
}
