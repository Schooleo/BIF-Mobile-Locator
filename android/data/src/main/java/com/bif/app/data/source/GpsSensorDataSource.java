package com.bif.app.data.source;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;

import javax.inject.Inject;
import dagger.hilt.android.qualifiers.ApplicationContext;

public class GpsSensorDataSource {

    private final FusedLocationProviderClient fusedLocationClient;
    private CancellationTokenSource cancellationTokenSource;

    @Inject
    public GpsSensorDataSource(@ApplicationContext Context context) {
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @SuppressLint("MissingPermission")
    public Task<android.location.Location> getCurrentLocation() {
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
        }
        cancellationTokenSource = new CancellationTokenSource();
        return fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken());
    }

    @SuppressLint("MissingPermission")
    public void requestLocationUpdates(LocationCallback callback) {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper());
    }

    public void removeLocationUpdates(LocationCallback callback) {
        if (callback != null) {
            fusedLocationClient.removeLocationUpdates(callback);
        }
        if (cancellationTokenSource != null) {
            cancellationTokenSource.cancel();
            cancellationTokenSource = null;
        }
    }
}