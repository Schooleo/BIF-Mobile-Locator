package com.bif.app.domain.repository;

import com.bif.app.domain.model.Location;

public interface LocationCallback {
    void onLocationResult(Location location);
    void onError(String message);
}
