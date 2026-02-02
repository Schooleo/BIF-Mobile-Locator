package com.bif.locator.domain.repository;

import com.bif.locator.domain.model.MapState;

public interface IMapRepository {
    void saveMapState(MapState state);
    MapState getMapState();
}
