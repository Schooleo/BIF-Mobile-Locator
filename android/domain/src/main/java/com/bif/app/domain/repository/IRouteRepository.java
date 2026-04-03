package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Route;

import java.util.List;

public interface IRouteRepository {
    LiveData<Route> getRoute(List<Location> waypoints);

    default LiveData<Boolean> observeOnlineStatus() {
        return new MutableLiveData<>(false);
    }

    default LiveData<OfflineMapDownloadState> observeOfflineCityMapDownloadState() {
        return new MutableLiveData<>(OfflineMapDownloadState.idle());
    }

    default void requestOfflineCityMapDownload(Location origin) {
        // Optional operation for repositories that support city map downloads.
    }
}
