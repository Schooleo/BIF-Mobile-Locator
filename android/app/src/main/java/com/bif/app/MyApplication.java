package com.bif.app;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.work.Configuration;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.data.sync.SyncWorker;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class MyApplication extends Application implements Configuration.Provider {

    @Inject
    HiltWorkerFactory workerFactory;

    @Inject
    SyncManager syncManager;

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
}