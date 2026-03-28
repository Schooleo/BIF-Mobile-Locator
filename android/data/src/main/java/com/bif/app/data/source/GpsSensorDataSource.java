package com.bif.app.data.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;

public class GpsSensorDataSource {

    public interface LocationUpdateListener {
        void onLocation(Location location);
        void onError(String message);
    }

    private final LocationManager locationManager;
    private LocationListener activeListener;

    @Inject
    public GpsSensorDataSource(@ApplicationContext Context context) {
        this.locationManager = context.getSystemService(LocationManager.class);
    }

    @SuppressLint("MissingPermission")
    public Location getCurrentLocation() {
        if (locationManager == null) {
            return null;
        }

        String provider = resolveBestProvider();
        if (provider == null) {
            return null;
        }

        return locationManager.getLastKnownLocation(provider);
    }

    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(LocationUpdateListener callback) {
        if (locationManager == null) {
            callback.onError("Location service unavailable");
            return;
        }

        String provider = resolveBestProvider();
        if (provider == null) {
            callback.onError("No location provider available");
            return;
        }

        removeLocationUpdates();
        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                callback.onLocation(location);
            }
        };

        locationManager.requestLocationUpdates(provider, 5000L, 5f,
                activeListener, Looper.getMainLooper());
    }

    public void removeLocationUpdates() {
        if (locationManager != null && activeListener != null) {
            locationManager.removeUpdates(activeListener);
            activeListener = null;
        }
    }

    private String resolveBestProvider() {
        if (locationManager == null) {
            return null;
        }

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        String bestProvider = locationManager.getBestProvider(criteria, true);
        if (bestProvider != null) {
            return bestProvider;
        }

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return LocationManager.GPS_PROVIDER;
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            return LocationManager.NETWORK_PROVIDER;
        }
        return null;
    }
}