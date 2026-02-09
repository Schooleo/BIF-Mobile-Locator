package com.bif.locator.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.bif.locator.domain.model.MapState;
import com.bif.locator.domain.repository.IMapRepository;
import javax.inject.Inject;
import dagger.hilt.android.qualifiers.ApplicationContext;

public class MapRepository implements IMapRepository {

    private final SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "map_prefs";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_ZOOM = "zoom";

    @Inject
    public MapRepository(@ApplicationContext Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void saveMapState(MapState state) {
        sharedPreferences.edit()
                .putFloat(KEY_LAT, (float) state.latitude)
                .putFloat(KEY_LNG, (float) state.longitude)
                .putFloat(KEY_ZOOM, state.zoomLevel)
                .apply();
    }

    @Override
    public MapState getMapState() {
        if (!sharedPreferences.contains(KEY_LAT)) {
            return null;
        }

        double lat = (double) sharedPreferences.getFloat(KEY_LAT, 0); // Default to 0 or some other default
        double lng = (double) sharedPreferences.getFloat(KEY_LNG, 0);
        float zoom = sharedPreferences.getFloat(KEY_ZOOM, 15f); // Default zoom
        return new MapState(lat, lng, zoom);
    }
}
