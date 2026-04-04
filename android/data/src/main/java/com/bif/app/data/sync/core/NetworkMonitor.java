package com.bif.app.data.sync.core;

import android.Manifest;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class NetworkMonitor {

    private final ConnectivityManager connectivityManager;
    private final MutableLiveData<Boolean> isConnected = new MutableLiveData<>(false);

    @Inject
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    public NetworkMonitor(@ApplicationContext Context context) {
        this.connectivityManager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        registerCallback();
        checkCurrentState();
    }

    public LiveData<Boolean> observeConnectivity() {
        return isConnected;
    }

    public boolean isOnline() {
        Boolean value = isConnected.getValue();
        return value != null && value;
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private void checkCurrentState() {
        Network active = connectivityManager.getActiveNetwork();
        if (active != null) {
            NetworkCapabilities caps =
                    connectivityManager.getNetworkCapabilities(active);
            isConnected.postValue(caps != null
                    && caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET));
        } else {
            isConnected.postValue(false);
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    private void registerCallback() {
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        connectivityManager.registerNetworkCallback(request,
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        isConnected.postValue(true);
                    }

                    @Override
                    public void onLost(@NonNull Network network) {
                        isConnected.postValue(false);
                    }
                });
    }
}

