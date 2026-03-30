package com.bif.app.feature.map;

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

public class MapStyleUtils {

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
            } catch (Exception ignored) {
                // Ignore unsupported layer/property combinations in third-party styles.
            }
        }
    }

    private static String buildLayerKey(Layer layer) {
        String sourceLayer = "";
        if (layer instanceof FillLayer) {
            sourceLayer = ((FillLayer) layer).getSourceLayer();
        } else if (layer instanceof LineLayer) {
            sourceLayer = ((LineLayer) layer).getSourceLayer();
        } else if (layer instanceof SymbolLayer) {
            sourceLayer = ((SymbolLayer) layer).getSourceLayer();
        } else if (layer instanceof CircleLayer) {
            sourceLayer = ((CircleLayer) layer).getSourceLayer();
        }
        return (layer.getId() + " " + sourceLayer).toLowerCase(Locale.ROOT);
    }

    private static void applyFillPalette(FillLayer layer, String key, boolean darkMode) {
        if (key.contains("water")) {
            layer.setProperties(
                    PropertyFactory.fillColor(Color.parseColor(
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

        layer.setProperties(
                PropertyFactory.fillColor(Color.parseColor(
                        darkMode ? "#142235" : "#F3F9FB")));
    }

    private static void applyLinePalette(LineLayer layer, String key, boolean darkMode) {
        if (key.contains("casing") || key.contains("outline")
                || key.contains("bridge") || key.contains("tunnel")) {
            layer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(
                            darkMode ? "#1F3348" : "#A5C0D0")));
            return;
        }

        if (key.contains("motorway") || key.contains("trunk")
                || key.contains("highway")) {
            layer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(
                            darkMode ? "#2D8E9D" : "#4AA2C0")));
            return;
        }

        if (key.contains("road") || key.contains("street")) {
            layer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(
                            darkMode ? "#2F4D66" : "#7FB6C4")));
            return;
        }

        if (key.contains("admin") || key.contains("boundary")) {
            layer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(
                            darkMode ? "#2C3C4F" : "#A6C1BE")));
            return;
        }

        if (key.contains("rail") || key.contains("transit")) {
            layer.setProperties(
                    PropertyFactory.lineColor(Color.parseColor(
                            darkMode ? "#35506A" : "#87AFC4")));
            return;
        }

        // Default line fallback prevents unclassified white line layers.
        layer.setProperties(
                PropertyFactory.lineColor(Color.parseColor(
                        darkMode ? "#2A435C" : "#95B6C8")));
    }

    private static void applySymbolPalette(SymbolLayer layer, String key,
            boolean darkMode) {
        float haloWidth = darkMode ? 0.85f : 0.55f;
        float haloBlur = darkMode ? 0.18f : 0.10f;
        int haloColor = Color.parseColor(darkMode ? "#E6000000" : "#F2FFFFFF");

        if (isPoiLikeLayer(key)) {
            applyPoiSymbolPalette(layer, darkMode);
            return;
        }

        int textColor = Color.parseColor(darkMode ? "#9CAFC3" : "#4F8A74");
        int iconColor = textColor;

        if (key.contains("country")) {
            textColor = Color.parseColor(darkMode ? "#A7C4D6" : "#2D6678");
            iconColor = textColor;
        } else if (key.contains("locality") || key.contains("place")) {
            textColor = Color.parseColor(darkMode ? "#B6CBDD" : "#3A7A76");
            iconColor = textColor;
        } else if (key.contains("water")) {
            textColor = Color.parseColor(darkMode ? "#7FAFD4" : "#2E6D8F");
            iconColor = textColor;
        }

        layer.setProperties(
                PropertyFactory.textColor(textColor),
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(haloWidth),
                PropertyFactory.textHaloBlur(haloBlur),
                PropertyFactory.iconColor(iconColor));
    }

    private static void applyCirclePalette(CircleLayer layer, String key,
            boolean darkMode) {
        if (isPoiLikeLayer(key)) {
            applyPoiCirclePalette(layer, key, darkMode);
            return;
        }

        int color = Color.parseColor(darkMode ? "#8EABC6" : "#7FAAB9");
        layer.setProperties(
                PropertyFactory.circleColor(color));
    }

    private static boolean isPoiLikeLayer(@NonNull String key) {
        return key.contains("poi")
                || key.contains("park")
                || key.contains("garden")
                || key.contains("restaurant")
                || key.contains("food")
                || key.contains("cafe")
                || key.contains("bar")
                || key.contains("shop")
                || key.contains("market")
                || key.contains("mall")
                || key.contains("retail")
                || key.contains("hospital")
                || key.contains("medical")
                || key.contains("clinic")
                || key.contains("pharmacy")
                || key.contains("school")
                || key.contains("college")
                || key.contains("university")
                || key.contains("library")
                || key.contains("museum")
                || key.contains("cinema")
                || key.contains("theatre")
                || key.contains("art")
                || key.contains("hotel")
                || key.contains("lodging")
                || key.contains("station")
                || key.contains("transit")
                || key.contains("bus");
    }

    private static void applyPoiSymbolPalette(@NonNull SymbolLayer layer, boolean darkMode) {
        float haloWidth = darkMode ? 0.8f : 0.9f;
        float iconHaloWidth = haloWidth + 0.5f;
        int haloColor = Color.parseColor(darkMode ? "#E6000000" : "#F2FFFFFF");

        layer.setProperties(
                PropertyFactory.textHaloColor(haloColor),
                PropertyFactory.textHaloWidth(haloWidth),
                PropertyFactory.iconHaloColor(haloColor),
                PropertyFactory.iconHaloWidth(iconHaloWidth));
    }

    private static void applyPoiCirclePalette(@NonNull CircleLayer layer,
            @NonNull String key,
            boolean darkMode) {
        int poiGeometry = darkMode
                ? Color.parseColor("#1B2633")
                : Color.parseColor("#E8EAED");
        int parkGeometry = darkMode
                ? Color.parseColor("#1F3A33")
                : Color.parseColor("#D2E8D8");

        int color = (key.contains("park") || key.contains("garden"))
                ? parkGeometry
                : poiGeometry;

        layer.setProperties(PropertyFactory.circleColor(color));
    }
}
