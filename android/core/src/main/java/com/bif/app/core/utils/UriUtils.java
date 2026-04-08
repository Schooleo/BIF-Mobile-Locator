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
        SOCIAL_CHAT,
        TRIP_DETAIL,
        FRIEND_SETTINGS_LOCATIONS,
        FRIEND_SETTINGS_TRIPS,
        GROUP_SETTINGS_PLANS,
        GROUP_SETTINGS_LOCATIONS,
        GROUP_SETTINGS_MEMBERS,
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
            case SOCIAL_CHAT:
                return buildUri("/social/chat");
            case TRIP_DETAIL:
                return buildUri("/social/trip-detail");
            case FRIEND_SETTINGS_LOCATIONS:
                return buildUri("/social/friend-settings/locations");
            case FRIEND_SETTINGS_TRIPS:
                return buildUri("/social/friend-settings/trips");
            case GROUP_SETTINGS_PLANS:
                return buildUri("/social/group-settings/plans");
            case GROUP_SETTINGS_LOCATIONS:
                return buildUri("/social/group-settings/locations");
            case GROUP_SETTINGS_MEMBERS:
                return buildUri("/social/group-settings/members");
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
