package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Route;

import java.util.List;

public interface IRouteRepository {
    LiveData<Route> getRoute(List<Location> waypoints);
}
