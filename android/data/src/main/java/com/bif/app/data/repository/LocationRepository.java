package com.bif.app.data.repository;

import com.bif.app.data.source.GoogleMapsDataSource;
import com.bif.app.data.source.GpsSensorDataSource;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.repository.ILocationRepository;
import com.google.android.gms.location.LocationResult;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import javax.inject.Inject;

public class LocationRepository implements ILocationRepository {

    private final GpsSensorDataSource gpsSensorDataSource;
    private com.google.android.gms.location.LocationCallback mappedFusedCallback;

    @Inject
    public LocationRepository(GpsSensorDataSource gpsSensorDataSource,
                              GoogleMapsDataSource googleMapsDataSource) {
        this.gpsSensorDataSource = gpsSensorDataSource;
    }

    @Override
    public LiveData<Location> getCurrentLocation() {
        MutableLiveData<Location> result = new MutableLiveData<>();

        gpsSensorDataSource.getCurrentLocation()
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
                .addOnFailureListener(e -> result.postValue(null));

        return result;
    }

    @Override
    public void requestLocationUpdates(com.bif.app.domain.repository.LocationCallback callback) {
        mappedFusedCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                android.location.Location lastLocation = locationResult.getLastLocation();
                if (lastLocation != null) {
                    Location domainLocation = new Location();
                    domainLocation.latitude = lastLocation.getLatitude();
                    domainLocation.longitude = lastLocation.getLongitude();
                    callback.onLocationResult(domainLocation);
                }
            }
        };

        gpsSensorDataSource.requestLocationUpdates(mappedFusedCallback);
    }

    @Override
    public void removeLocationUpdates(com.bif.app.domain.repository.LocationCallback callback) {
        if (mappedFusedCallback != null) {
            gpsSensorDataSource.removeLocationUpdates(mappedFusedCallback);
            mappedFusedCallback = null;
        }
    }
}