package com.bif.app.data.sync;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.hilt.work.HiltWorker;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bif.app.data.source.local.ProfileDao;
import com.bif.app.data.source.local.TripDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@HiltWorker
public class StorageCleanupWorker extends Worker {

    private static final String WORK_NAME = "storage_cleanup_work";

    private final ProfileDao profileDao;
    private final TripDao tripDao;

    @AssistedInject
    public StorageCleanupWorker(
            @Assisted @NonNull Context context,
            @Assisted @NonNull WorkerParameters params,
            ProfileDao profileDao,
            TripDao tripDao) {
        super(context, params);
        this.profileDao = profileDao;
        this.tripDao = tripDao;
    }

    @NonNull
    @Override
    public Result doWork() {
        cleanupSyncedRecords();
        cleanupOrphanFiles();
        return Result.success();
    }

    private void cleanupSyncedRecords() {
        List<ProfileEntity> profiles = profileDao.getSyncedWithLocalImagePath();
        for (ProfileEntity profile : profiles) {
            String path = profile.localImagePath;
            deleteIfExists(path);
            profile.localImagePath = null;
            profileDao.upsert(profile);
        }

        List<TripStopEntity> tripStops = tripDao.getSyncedStopsWithLocalImagePath();
        for (TripStopEntity stop : tripStops) {
            String path = stop.localImagePath;
            deleteIfExists(path);
            stop.localImagePath = null;
            tripDao.upsertStop(stop);
        }
    }

    private void cleanupOrphanFiles() {
        Set<String> referenced = new HashSet<>();
        referenced.addAll(profileDao.getAllReferencedLocalImagePaths());
        referenced.addAll(tripDao.getAllReferencedLocalImagePaths());

        File stagingDir = new File(getApplicationContext().getFilesDir(),
                "image-staging");
        File[] files = stagingDir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file == null) {
                continue;
            }
            if (!referenced.contains(file.getAbsolutePath())) {
                file.delete();
            }
        }
    }

    private void deleteIfExists(String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                StorageCleanupWorker.class,
                7,
                TimeUnit.DAYS)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }
}
