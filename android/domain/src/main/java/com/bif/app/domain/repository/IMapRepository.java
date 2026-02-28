package com.bif.app.domain.repository;

import com.bif.app.domain.model.MapState;

public interface IMapRepository {
    void saveMapState(MapState state);
    MapState getMapState();
}
