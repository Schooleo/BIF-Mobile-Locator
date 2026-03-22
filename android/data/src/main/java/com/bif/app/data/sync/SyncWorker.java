package com.bif.app.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";
    private static final String WORK_NAME = "periodic_sync";

    public SyncWorker(@NonNull Context context,
                      @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Periodic sync started");
        // SyncManager should be obtained via the application component
        // in a production app. This worker is schedulable by the code below.
        // The actual sync logic in doWork depends on DI availability.
        // For HiltWorker integration, extend HiltWorker and inject SyncManager.
        return Result.success();
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
