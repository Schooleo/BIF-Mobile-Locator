package com.bif.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.repository.IMapRepository;
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
                .putLong(KEY_LAT, Double.doubleToRawLongBits(state.latitude))
                .putLong(KEY_LNG, Double.doubleToRawLongBits(state.longitude))
                .putFloat(KEY_ZOOM, state.zoomLevel)
                .apply();
    }

    @Override
    public MapState getMapState() {
        if (!sharedPreferences.contains(KEY_LAT)) {
            return null;
        }

        double latitude;
        double longitude;
        try {
            // New format: stored as long bits of a double
            latitude = Double.longBitsToDouble(sharedPreferences.getLong(KEY_LAT, 0));
            longitude = Double.longBitsToDouble(sharedPreferences.getLong(KEY_LNG, 0));
        } catch (ClassCastException e) {
            // Legacy format: stored as float; read, convert, and migrate to the new format
            float latFloat = sharedPreferences.getFloat(KEY_LAT, 0f);
            float lngFloat = sharedPreferences.getFloat(KEY_LNG, 0f);
            latitude = latFloat;
            longitude = lngFloat;
            sharedPreferences.edit()
                    .putLong(KEY_LAT, Double.doubleToRawLongBits(latitude))
                    .putLong(KEY_LNG, Double.doubleToRawLongBits(longitude))
                    .apply();
        }

        float zoomLevel = sharedPreferences.getFloat(KEY_ZOOM, 15f);

        return new MapState(latitude, longitude, zoomLevel);
    }
}
