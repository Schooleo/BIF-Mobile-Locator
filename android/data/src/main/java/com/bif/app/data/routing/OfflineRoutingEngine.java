package com.bif.app.data.routing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Route;

import java.io.File;
import java.util.List;

public interface OfflineRoutingEngine {
    boolean isReady(@NonNull File mapDataFile);

    @Nullable
    Route route(@NonNull List<Location> waypoints,
                @NonNull String profile,
                @NonNull File mapDataFile);
}
