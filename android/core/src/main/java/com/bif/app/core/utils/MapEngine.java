package com.bif.app.core.utils;

public enum MapEngine {
    GOOGLE,
    OSM;

    public static MapEngine fromValue(String value) {
        if (value == null || value.isBlank()) {
            return GOOGLE;
        }
        try {
            return MapEngine.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return GOOGLE;
        }
    }
}
