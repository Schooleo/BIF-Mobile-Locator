package com.bif.app.core.utils;

import android.net.Uri;

public class UriUtils {
    private static final String defaultScheme = "app";
    private static final String defaultAuthority = "bif.app";
    private static final String defaultPath = "/map";

    public enum PathTo {
        MAP,
        FAVORITES,
        FAVORITES_DETAIL,
        SOCIAL,
        GROUP_DETAIL,
        PROFILE,
        LOGIN,
        REGISTER,
        PERSONAL_INFO
    }

    public static Uri buildUri(String scheme, String authority, String path) {
        return new Uri.Builder()
            .scheme(scheme)
            .authority(authority)
            .path(path)
            .build();
    }

    public static Uri buildUri(String path) {
        if (path.isEmpty()) path = defaultPath;
        if (!path.startsWith("/")) path = "/" + path;

        return buildUri(defaultScheme, defaultAuthority, path);
    }

    public static Uri buildUri(PathTo dest) {
        switch (dest) {
            case FAVORITES:
                return buildUri("/favorites");
            case FAVORITES_DETAIL:
                return buildUri("/favorites/detail");
            case SOCIAL:
                return buildUri("/social");
            case GROUP_DETAIL:
                return buildUri("/social/group-detail");
            case PROFILE:
                return buildUri("/profile");
            case LOGIN:
                return buildUri("/login");
            case REGISTER:
                return buildUri("/register");
            case PERSONAL_INFO:
                return buildUri("/profile/personal-info");
            case MAP:
            default:
                return buildUri(defaultPath);
        }
    }

    public static Uri buildUri() {
        return buildUri(defaultPath);
    }
}
