package com.bif.app.feature.map;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bif.app.core.utils.MapPaletteUtils;

import org.maplibre.android.maps.Style;

public final class MapStyleUtils {

    private MapStyleUtils() {
    }

    public static void applyPaletteForCurrentMode(@NonNull Context context,
                                                  @NonNull Style style) {
        MapPaletteUtils.applyPaletteForCurrentMode(context, style);
    }
}
