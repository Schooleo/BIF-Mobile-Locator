package com.bif.app.feature.social;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import androidx.annotation.NonNull;

import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.SymbolLayer;

import java.util.List;
import java.util.Locale;

public final class SocialMapStyleUtils {

    private SocialMapStyleUtils() {
    }

    public static void applyPaletteForCurrentMode(@NonNull Context context, @NonNull Style style) {
        applyPalette(style, isDarkMode(context));
    }

    private static boolean isDarkMode(@NonNull Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    private static void applyPalette(Style style, boolean darkMode) {
        List<Layer> layers = style.getLayers();
        for (Layer layer : layers) {
            try {
                if (layer instanceof BackgroundLayer) {
                    layer.setProperties(
                            PropertyFactory.backgroundColor(Color.parseColor(
                                    darkMode ? "#0E1726" : "#EEF7FA")));
                    continue;
                }

                String key = buildLayerKey(layer);
                if (layer instanceof FillLayer) {
                    applyFillPalette((FillLayer) layer, key, darkMode);
                } else if (layer instanceof LineLayer) {
                    applyLinePalette((LineLayer) layer, key, darkMode);
                } else if (layer instanceof SymbolLayer) {
                    applySymbolPalette((SymbolLayer) layer, key, darkMode);
                } else if (layer instanceof CircleLayer) {
                    applyCirclePalette((CircleLayer) layer, key, darkMode);
                }
            } catch (RuntimeException e) {
                // Log but continue on unsupported layer/property combinations in third-party styles.
                android.util.Log.w("SocialMapStyleUtils","Failed to apply palette to layer: " + layer.getId(), e);
            }
        }
    }

    private static String buildLayerKey(Layer layer) {
        String sourceLayer = null;
        if (layer instanceof FillLayer) {
            sourceLayer = ((FillLayer) layer).getSourceLayer();
        } else if (layer instanceof LineLayer) {
            sourceLayer = ((LineLayer) layer).getSourceLayer();
        } else if (layer instanceof SymbolLayer) {
            sourceLayer = ((SymbolLayer) layer).getSourceLayer();
        } else if (layer instanceof CircleLayer) {
            sourceLayer = ((CircleLayer) layer).getSourceLayer();
        }
        String layerKey = layer.getId();
        if (sourceLayer != null) {
            layerKey += " " + sourceLayer;
        }
        return layerKey.toLowerCase(Locale.ROOT);
    }

    private static void applyFillPalette(FillLayer layer, String key, boolean darkMode) {
        if (key.contains("water")) {
            layer.setProperties(PropertyFactory.fillColor(Color.parseColor(
                    darkMode ? "#0A2A45" : "#CBE5F6")));
            return;
        }

        if (key.contains("park") || key.contains("landuse")) {
            layer.setProperties(
                    PropertyFactory.fillColor(Color.parseColor(
                            darkMode ? "#173A36" : "#DEEFE9")),
                    PropertyFactory.fillOutlineColor(Color.parseColor(
                            darkMode ? "#173A36" : "#DEEFE9")));
            return;
        }

        layer.setProperties(PropertyFactory.fillColor(Color.parseColor(
                darkMode ? "#142235" : "#F3F9FB")));
    }

    private static void applyLinePalette(LineLayer layer, String key, boolean darkMode) {
        if (key.contains("casing") || key.contains("outline")
                || key.contains("bridge") || key.contains("tunnel")) {
            layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                    darkMode ? "#1F3348" : "#A5C0D0")));
            return;
        }

        if (key.contains("motorway") || key.contains("trunk")
                || key.contains("highway")) {
            layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                    darkMode ? "#2D8E9D" : "#4AA2C0")));
            return;
        }

        if (key.contains("road") || key.contains("street")) {
            layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                    darkMode ? "#2F4D66" : "#7FB6C4")));
            return;
        }

        if (key.contains("admin") || key.contains("boundary")) {
            layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                    darkMode ? "#2C3C4F" : "#A6C1BE")));
            return;
        }

        if (key.contains("rail") || key.contains("transit")) {
            layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                    darkMode ? "#35506A" : "#87AFC4")));
            return;
        }

        layer.setProperties(PropertyFactory.lineColor(Color.parseColor(
                darkMode ? "#2A435C" : "#95B6C8")));
    }

    private static void applySymbolPalette(SymbolLayer layer, String key, boolean darkMode) {
        float haloWidth = darkMode ? 0.85f : 0.55f;
        float haloBlur = darkMode ? 0.18f : 0.10f;
        int haloColor = Color.parseColor(darkMode ? "#E6000000" : "#F2FFFFFF");

        int textColor = Color.parseColor(darkMode ? "#9CAFC3" : "#4F8A74");
        int iconColor = textColor;

        if (key.contains("country")) {
            textColor = Color.parseColor(darkMode ? "#A7C4D6" : "#2D6678");
            iconColor = textColor;
        } else if (key.contains("locality") || key.contains("place")) {
            textColor = Color.parseColor(darkMode ? "#B6CBDD" : "#3A7A76");
            iconColor = textColor;
        }

        layer.setProperties(
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(haloWidth),
                PropertyFactory.textHaloBlur(haloBlur),
                PropertyFactory.iconColor(iconColor));
    }

    private static void applyCirclePalette(CircleLayer layer, String key, boolean darkMode) {
        int color = key.contains("park")
                ? Color.parseColor(darkMode ? "#1F3A33" : "#D2E8D8")
                : Color.parseColor(darkMode ? "#1B2633" : "#E8EAED");
        layer.setProperties(PropertyFactory.circleColor(color));
    }
}
