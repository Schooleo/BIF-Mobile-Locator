package com.bif.app.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@HiltWorker
public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final String WORK_NAME = "periodic_sync";

    private final SyncManager syncManager;

    @AssistedInject
    public SyncWorker(
            @Assisted @NonNull Context context,
            @Assisted @NonNull WorkerParameters params,
            SyncManager syncManager) {
        super(context, params);
        this.syncManager = syncManager;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Periodic sync started");
        try {
            syncManager.sync();
            Log.d(TAG, "Periodic sync completed");
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Periodic sync failed", e);
            return Result.retry();
        }
    }

    /**
     * Schedule periodic sync with network connectivity constraint.
     * Call this from Application.onCreate() or after login.
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);

        Log.d(TAG, "Periodic sync scheduled");
    }

    /**
     * Cancel periodic sync.
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }
}
