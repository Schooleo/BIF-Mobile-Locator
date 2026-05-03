package com.bif.app.data.repository;

import com.bif.app.data.source.GpsSensorDataSource;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.repository.ILocationRepository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import javax.inject.Inject;

public class LocationRepository implements ILocationRepository {

    private final GpsSensorDataSource gpsSensorDataSource;

    @Inject
    public LocationRepository(GpsSensorDataSource gpsSensorDataSource) {
        this.gpsSensorDataSource = gpsSensorDataSource;
    }

    @Override
    public LiveData<Location> getCurrentLocation() {
        MutableLiveData<Location> result = new MutableLiveData<>();

        android.location.Location location = gpsSensorDataSource
                .getCurrentLocation();
        if (location == null) {
            result.postValue(null);
            return result;
        }

        Location currentLocation = new Location();
        currentLocation.latitude = location.getLatitude();
        currentLocation.longitude = location.getLongitude();
        result.postValue(currentLocation);

        return result;
    }

    @Override
    public void requestLocationUpdates(com.bif.app.domain.repository.LocationCallback callback) {
        gpsSensorDataSource.requestLocationUpdates(
                new GpsSensorDataSource.LocationUpdateListener() {
            @Override
            public void onLocation(android.location.Location location) {
                if (location != null) {
                    Location domainLocation = new Location();
                    domainLocation.latitude = location.getLatitude();
                    domainLocation.longitude = location.getLongitude();
                    callback.onLocationResult(domainLocation);
                }
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void removeLocationUpdates(com.bif.app.domain.repository.LocationCallback callback) {
        gpsSensorDataSource.removeLocationUpdates();
    }
}