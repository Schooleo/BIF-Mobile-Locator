package com.bif.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import com.bif.app.core.auth.LocalSessionDataCleaner;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.data.sync.SyncWorker;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApplication extends Application implements Configuration.Provider {

    private static final String SYNC_PREF_NAME = "SYNC_PREF";
    private static final String APP_GUARD_PREF_NAME = "APP_GUARD_PREF";
    private static final String KEY_SESSION_SCHEMA_VERSION = "session_schema_version";
    private static final int SESSION_SCHEMA_VERSION = 2;

    @Inject
    HiltWorkerFactory workerFactory;

    @Inject
    SyncManager syncManager;

    @Inject
    LocalSessionDataCleaner localSessionDataCleaner;

    @Override
    public void onCreate() {
        super.onCreate();

        String userId = UserPreferences.getId(this);
        if (userId.trim().isEmpty()) {
            userId = UserPreferences.getUsername(this);
        }

        if (!userId.trim().isEmpty()) {
            syncManager.setUserContext(userId, null);
        }

        SyncWorker.schedule(this);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }

    private void clearIncompatibleLocalSessionIfNeeded() {
        if (!BuildConfig.DEBUG) {
            return;
        }

        SharedPreferences guardPrefs = getSharedPreferences(
                APP_GUARD_PREF_NAME, Context.MODE_PRIVATE);
        int storedVersion = guardPrefs.getInt(KEY_SESSION_SCHEMA_VERSION, 0);
        if (storedVersion == SESSION_SCHEMA_VERSION) {
            return;
        }

        UserPreferences.clearUser(this);
        getSharedPreferences(SYNC_PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
        localSessionDataCleaner.clearLocalUserData();

        guardPrefs.edit()
                .putInt(KEY_SESSION_SCHEMA_VERSION, SESSION_SCHEMA_VERSION)
                .apply();
    }
}