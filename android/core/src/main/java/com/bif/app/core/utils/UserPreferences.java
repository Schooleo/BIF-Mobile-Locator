package com.bif.app.core.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UserPreferences {
    private static final String PREF_NAME = "USER_PREF";

    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";

    private static final IPasswordEncoder encoder = new BcryptPasswordEncoder();
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void saveUser(Context context, String username,
                                String password, String email) {
        String encodedPassword = encoder.encode(password);

        getPrefs(context).edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, encodedPassword)
                .putString(KEY_EMAIL, email)
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .apply();
    }

    public static String getUsername(Context context) {
        return getPrefs(context).getString(KEY_USERNAME, "");
    }

    public static String getPassword(Context context) {
        return getPrefs(context).getString(KEY_PASSWORD, "");
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

    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public static void setLoggedIn(Context context, boolean isLoggedIn) {
        getPrefs(context).edit()
                .putBoolean(KEY_IS_LOGGED_IN, isLoggedIn)
                .apply();
    }

    public static boolean checkPassword(Context context, String password) {
        String encodedPassword = getPassword(context);
        return encoder.verify(password, encodedPassword);
    }

    public static void clearUser(Context context){
        getPrefs(context).edit()
                .clear()
                .apply();
    }
}
