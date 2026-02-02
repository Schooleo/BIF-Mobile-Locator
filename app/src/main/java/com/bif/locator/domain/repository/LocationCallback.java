package com.bif.locator.domain.repository;

import com.bif.locator.domain.model.Location;

public interface LocationCallback {
    void onLocationResult(Location location);
    void onError(String message);
}
