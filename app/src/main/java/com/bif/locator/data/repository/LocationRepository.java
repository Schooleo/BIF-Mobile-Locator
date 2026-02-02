package com.bif.locator.data.repository;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import com.bif.locator.domain.model.Location;
import com.bif.locator.domain.repository.ILocationRepository;

import java.io.IOException;
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

    @Inject
    public LocationRepository(@ApplicationContext Context context) {
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<Location> getCurrentLocation() {
        return null; // TODO: Implement GPS logic
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
                e.printStackTrace();
                result.postValue(null);
            }
        });
        
        return result;
    }

    @Override
    public void requestLocationUpdates(com.bif.locator.domain.repository.LocationCallback callback) {
        // TODO: Implement location updates
    }

    @Override
    public void removeLocationUpdates(com.bif.locator.domain.repository.LocationCallback callback) {
        // TODO: Remove location updates
    }

    @Override
    public LiveData<List<com.bif.locator.domain.model.Place>> searchPlaces(String query) {
        // TODO: Implement Places API search
        return new MutableLiveData<>();
    }
}
