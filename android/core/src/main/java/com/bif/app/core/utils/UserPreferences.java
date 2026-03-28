package com.bif.app.core.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    private static final String PREF_NAME = "USER_PREF";

    private static final String KEY_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_MAP_ENGINE = "map_engine";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveUserProfile(Context context, String id, String username, String email) {
        getPrefs(context).edit()
                .putString(KEY_ID, id)
                .putString(KEY_USERNAME, username)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    public static String getId(Context context) {
        return getPrefs(context).getString(KEY_ID, "");
    }

    public static String getUsername(Context context) {
        return getPrefs(context).getString(KEY_USERNAME, "");
    }

    public static String getEmail(Context context) {
        return getPrefs(context).getString(KEY_EMAIL, "");
    }

    public static void setUsername(Context context, String username) {
        getPrefs(context).edit()
                .putString(KEY_USERNAME, username)
                .apply();
    }

    public static String getAvatarUri(Context context) {
        return getPrefs(context).getString(KEY_AVATAR_URI, "");
    }

    public static void setAvatarUri(Context context, String avatarUri) {
        getPrefs(context).edit()
                .putString(KEY_AVATAR_URI, avatarUri)
                .apply();
    }

    public static String getAuthToken(Context context) {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, "");
    }

    public static void setAuthToken(Context context, String authToken) {
        getPrefs(context).edit()
                .putString(KEY_AUTH_TOKEN, authToken)
                .apply();
    }

    public static String getRefreshToken(Context context) {
        return getPrefs(context).getString(KEY_REFRESH_TOKEN, "");
    }

    public static void setRefreshToken(Context context, String refreshToken) {
        getPrefs(context).edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    public static void saveAuthSession(Context context, String authToken, String refreshToken) {
        getPrefs(context).edit()
                .putString(KEY_AUTH_TOKEN, authToken != null ? authToken : "")
                .putString(KEY_REFRESH_TOKEN, refreshToken != null ? refreshToken : "")
                .putBoolean(KEY_IS_LOGGED_IN, authToken != null && !authToken.isBlank())
                .apply();
    }

    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public static void setLoggedIn(Context context, boolean isLoggedIn) {
        getPrefs(context).edit()
                .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
                .apply();
    }

    public static void clearUser(Context context){
        getPrefs(context).edit()
                .clear()
                .apply();
    }

    public static MapEngine getMapEngine(Context context) {
        String raw = getPrefs(context).getString(KEY_MAP_ENGINE,
                MapEngine.OSM.name());
        return MapEngine.fromValue(raw);
    }

    public static void setMapEngine(Context context, MapEngine engine) {
        MapEngine safeEngine = engine != null ? engine : MapEngine.OSM;
        getPrefs(context).edit()
                .putString(KEY_MAP_ENGINE, safeEngine.name())
                .apply();
    }
}
