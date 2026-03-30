package com.bif.app.core.utils;

public enum MapEngine {
    OSM;

    public static MapEngine fromValue(String value) {
        return OSM;
    }
}
