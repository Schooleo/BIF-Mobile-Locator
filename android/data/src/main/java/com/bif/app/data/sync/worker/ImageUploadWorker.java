package com.bif.app.data.sync.worker;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.hilt.work.HiltWorker;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.media.UploadSignatureResponseDto;
import com.bif.app.core.network.dto.trip.TripPlanDto;
import com.bif.app.core.network.dto.trip.TripStopDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ProfileMapper;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.TripMemberCrossRef;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.bif.app.data.sync.core.SyncManager;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import retrofit2.Response;

@HiltWorker
public class ImageUploadWorker extends Worker {

    private static final String TAG = "ImageUploadWorker";
    private static final String WORK_NAME = "image_upload_work";
    private static final String PERIODIC_WORK_NAME = "image_upload_periodic_work";
    private static final int MAX_RUN_ATTEMPTS = 5;
    private static final int RETRY_BACKOFF_SECONDS = 30;

    private final RestApiService restApiService;
    private final ProfileDao profileDao;
    private final TripDao tripDao;
    private final SyncManager syncManager;

    @AssistedInject
    public ImageUploadWorker(
            @Assisted @NonNull Context context,
            @Assisted @NonNull WorkerParameters params,
            RestApiService restApiService,
            ProfileDao profileDao,
            TripDao tripDao,
            SyncManager syncManager) {
        super(context, params);
        this.restApiService = restApiService;
        this.profileDao = profileDao;
        this.tripDao = tripDao;
        this.syncManager = syncManager;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ProfileEntity pendingProfile = profileDao.getFirstPendingUpload();
            if (pendingProfile != null) {
                Log.d(TAG, "Processing pending profile upload, attempt="
                        + getRunAttemptCount());
                return uploadProfile(pendingProfile);
            }

            TripStopEntity pendingTripStop = tripDao.getFirstPendingUploadStop();
            if (pendingTripStop != null) {
                Log.d(TAG, "Processing pending trip stop upload, attempt="
                        + getRunAttemptCount());
                return uploadTripStop(pendingTripStop);
            }

            TripPlanEntity pendingTripCover = tripDao.getFirstPendingUploadTripCover();
            if (pendingTripCover != null) {
                Log.d(TAG, "Processing pending trip cover upload, attempt="
                        + getRunAttemptCount());
                return uploadTripCover(pendingTripCover);
            }
            return Result.success();
        } catch (Exception ex) {
            Log.e(TAG, "Unexpected upload failure", ex);
            if (shouldStopRetrying()) {
                Log.e(TAG, "Max retry attempts reached. Marking work as failure.");
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private Result uploadProfile(ProfileEntity profile) {
        if (!isValidLocalPath(profile.localImagePath)) {
            profile.uploadStatus = UploadStatus.ERROR;
            profileDao.upsert(profile);
            return Result.success();
        }

        profile.uploadStatus = UploadStatus.UPLOADING;
        profile.updatedAt = System.currentTimeMillis();
        profileDao.upsert(profile);

        try {
            Response<UploadSignatureResponseDto> response = restApiService
                    .getUploadSignature("avatar", null)
                    .execute();
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Failed to fetch profile upload signature. code="
                        + response.code());
                profile.uploadStatus = UploadStatus.ERROR;
                profileDao.upsert(profile);
                if (shouldStopRetrying()) {
                    return Result.failure();
                }
                return Result.retry();
            }

            if (!hasSignedPublicId(response.body())) {
                Log.e(TAG, "Profile signature missing server-signed publicId");
                profile.uploadStatus = UploadStatus.ERROR;
                profileDao.upsert(profile);
                return Result.failure();
            }

            UploadResult uploadResult = uploadFile(profile.localImagePath, response.body());
            if (!uploadResult.success) {
                Log.w(TAG, "Profile image upload failed: " + uploadResult.errorMessage);
                profile.uploadStatus = UploadStatus.ERROR;
                profileDao.upsert(profile);
                if (shouldStopRetrying()) {
                    return Result.failure();
                }
                return Result.retry();
            }

            String originalPath = profile.localImagePath;
            profile.avatarUrl = uploadResult.remoteUrl;
            profile.uploadStatus = UploadStatus.SYNCED;
            if (deleteLocalFile(originalPath)) {
                profile.localImagePath = null;
            } else {
                Log.w(TAG, "Profile local file could not be deleted: " + originalPath);
            }
            profile.updatedAt = System.currentTimeMillis();
            profileDao.upsert(profile);
            UserPreferences.setAvatarUri(getApplicationContext(), profile.avatarUrl);

            syncManager.enqueueChange(
                    "profile",
                    profile.userId,
                    "UPDATE",
                    UUID.randomUUID().toString(),
                    ProfileMapper.toDto(profile));
            syncManager.syncIfOnline();

            enqueue(getApplicationContext());
            return Result.success();
        } catch (IOException ioEx) {
            Log.e(TAG, "Profile upload I/O error", ioEx);
            profile.uploadStatus = UploadStatus.ERROR;
            profileDao.upsert(profile);
            if (shouldStopRetrying()) {
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private Result uploadTripStop(TripStopEntity tripStop) {
        if (!isValidLocalPath(tripStop.localImagePath)) {
            tripStop.uploadStatus = UploadStatus.ERROR;
            tripDao.upsertStop(tripStop);
            return Result.success();
        }

        tripStop.uploadStatus = UploadStatus.UPLOADING;
        tripDao.upsertStop(tripStop);

        try {
            Response<UploadSignatureResponseDto> response = restApiService
                    .getUploadSignature("trip_stop", tripStop.tripId)
                    .execute();
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Failed to fetch trip stop upload signature. code="
                        + response.code());
                tripStop.uploadStatus = UploadStatus.ERROR;
                tripDao.upsertStop(tripStop);
                if (shouldStopRetrying()) {
                    return Result.failure();
                }
                return Result.retry();
            }

            if (!hasSignedPublicId(response.body())) {
                Log.e(TAG, "Trip stop signature missing server-signed publicId");
                tripStop.uploadStatus = UploadStatus.ERROR;
                tripDao.upsertStop(tripStop);
                return Result.failure();
            }

            UploadResult uploadResult = uploadFile(tripStop.localImagePath,
                    response.body());
            if (!uploadResult.success) {
                Log.w(TAG, "Trip stop image upload failed: " + uploadResult.errorMessage);
                tripStop.uploadStatus = UploadStatus.ERROR;
                tripDao.upsertStop(tripStop);
                if (shouldStopRetrying()) {
                    return Result.failure();
                }
                return Result.retry();
            }

            String originalPath = tripStop.localImagePath;
            tripStop.photoUrl = uploadResult.remoteUrl;
            tripStop.uploadStatus = UploadStatus.SYNCED;
            if (deleteLocalFile(originalPath)) {
                tripStop.localImagePath = null;
            } else {
                Log.w(TAG, "Trip stop local file could not be deleted: " + originalPath);
            }
            tripDao.upsertStop(tripStop);

            syncManager.enqueueChange(
                    "trip_stop",
                    tripStop.id,
                    "UPDATE",
                    UUID.randomUUID().toString(),
                    toTripStopDto(tripStop));
            syncManager.syncIfOnline();

            enqueue(getApplicationContext());
            return Result.success();
        } catch (IOException ioEx) {
            Log.e(TAG, "Trip stop upload I/O error", ioEx);
            tripStop.uploadStatus = UploadStatus.ERROR;
            tripDao.upsertStop(tripStop);
            if (shouldStopRetrying()) {
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private Result uploadTripCover(TripPlanEntity tripPlan) {
        if (!isValidLocalPath(tripPlan.localCoverImagePath)) {
            tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.ERROR);
            return Result.success();
        }

        tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.UPLOADING);

        try {
            Response<UploadSignatureResponseDto> response = restApiService
                    .getUploadSignature("trip_plan", tripPlan.id)
                    .execute();
            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Failed to fetch trip cover upload signature. code="
                        + response.code());
                if (shouldStopRetrying()) {
                    tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.ERROR);
                    return Result.failure();
                }
                tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.PENDING);
                return Result.retry();
            }

            if (!hasSignedPublicId(response.body())) {
                Log.e(TAG, "Trip cover signature missing server-signed publicId");
                tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.ERROR);
                return Result.failure();
            }

            UploadResult uploadResult = uploadFile(tripPlan.localCoverImagePath, response.body());
            if (!uploadResult.success) {
                Log.w(TAG, "Trip cover image upload failed: " + uploadResult.errorMessage);
                if (shouldStopRetrying()) {
                    tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.ERROR);
                    return Result.failure();
                }
                tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.PENDING);
                return Result.retry();
            }

            String originalPath = tripPlan.localCoverImagePath;
            String localCoverImagePath = originalPath;
            if (deleteLocalFile(originalPath)) {
                localCoverImagePath = null;
            } else {
                Log.w(TAG, "Trip cover local file could not be deleted: " + originalPath);
            }
            tripDao.updateTripCoverFields(
                    tripPlan.id,
                    uploadResult.remoteUrl,
                    UploadStatus.SYNCED,
                    localCoverImagePath);

            TripPlanEntity latestTripPlan = tripDao.getTripByIdSync(tripPlan.id);
            if (latestTripPlan == null) {
                latestTripPlan = tripPlan;
                latestTripPlan.coverImageUrl = uploadResult.remoteUrl;
                latestTripPlan.coverUploadStatus = UploadStatus.SYNCED;
                latestTripPlan.localCoverImagePath = localCoverImagePath;
            }

            syncManager.enqueueChange(
                    "trip_plan",
                    latestTripPlan.id,
                    "UPDATE",
                    UUID.randomUUID().toString(),
                    toTripPlanDto(latestTripPlan,
                        mapParticipantIds(tripDao.getTripMembersSync(latestTripPlan.id))));
            syncManager.syncIfOnline();

            enqueue(getApplicationContext());
            return Result.success();
        } catch (IOException ioEx) {
            Log.e(TAG, "Trip cover upload I/O error", ioEx);
            if (shouldStopRetrying()) {
                tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.ERROR);
                return Result.failure();
            }
            tripDao.updateTripCoverUploadStatus(tripPlan.id, UploadStatus.PENDING);
            return Result.retry();
        }
    }

    private UploadResult uploadFile(String localPath,
            UploadSignatureResponseDto signature) {
        UploadResult result = new UploadResult();
        CountDownLatch latch = new CountDownLatch(1);

        Map<String, Object> options;
        try {
            options = buildUploadOptions(signature);
        } catch (IllegalArgumentException ex) {
            result.success = false;
            result.errorMessage = ex.getMessage();
            return result;
        }

        MediaManager.get().upload(localPath)
                .options(options)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                    }

                    @Override
                    public void onProgress(String requestId,
                            long bytes,
                            long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId,
                            Map resultData) {
                        Object secure = resultData.get("secure_url");
                        if (secure == null) {
                            secure = resultData.get("url");
                        }
                        if (secure != null) {
                            result.success = true;
                            result.remoteUrl = secure.toString();
                        } else {
                            result.success = false;
                            result.errorMessage = "Missing URL in upload response";
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(String requestId,
                            ErrorInfo error) {
                        result.success = false;
                        result.errorMessage = error != null
                                ? error.getDescription()
                                : "Upload failed";
                        latch.countDown();
                    }

                    @Override
                    public void onReschedule(String requestId,
                            ErrorInfo error) {
                        result.success = false;
                        result.errorMessage = error != null
                                ? error.getDescription()
                                : "Upload rescheduled";
                        latch.countDown();
                    }
                })
                .dispatch();

        try {
            boolean finished = latch.await(2, TimeUnit.MINUTES);
            if (!finished) {
                result.success = false;
                result.errorMessage = "Upload timeout";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.success = false;
            result.errorMessage = "Interrupted";
        }

        return result;
    }

    Map<String, Object> buildUploadOptions(UploadSignatureResponseDto signature) {
        if (signature == null) {
            throw new IllegalArgumentException("Missing signature response");
        }
        if (!hasSignedPublicId(signature)) {
            throw new IllegalArgumentException("Missing server-signed publicId");
        }

        Map<String, Object> options = new HashMap<>();
        String folder = signature.folder != null ? signature.folder.trim() : "";
        if (folder.isEmpty()) {
            throw new IllegalArgumentException("Missing folder in signature");
        }
        options.put("folder", folder);
        options.put("public_id", signature.publicId.trim());
        options.put("signature", signature.signature);
        options.put("timestamp", signature.timestamp);
        options.put("api_key", signature.apiKey);
        if (signature.tags != null && !signature.tags.trim().isEmpty()) {
            options.put("tags", signature.tags.trim());
        }
        return options;
    }

    private boolean hasSignedPublicId(UploadSignatureResponseDto signature) {
        return signature != null
                && signature.publicId != null
                && !signature.publicId.trim().isEmpty();
    }

    private TripStopDto toTripStopDto(TripStopEntity entity) {
        TripStopDto dto = new TripStopDto();
        dto.id = entity.id;
        dto.tripId = entity.tripId;
        dto.title = entity.title;
        dto.address = entity.address;
        dto.note = entity.note;
        dto.photoUrl = entity.photoUrl;
        dto.arrivalTime = formatInstant(entity.arrivalTime);
        dto.departureTime = formatInstant(entity.departureTime);
        dto.orderIndex = entity.orderIndex;
        dto.serverVersion = entity.serverVersion;
        dto.deleted = entity.deleted;

        ChatMessageDto.LocationDto location = new ChatMessageDto.LocationDto();
        location.latitude = entity.latitude;
        location.longitude = entity.longitude;
        dto.location = location;
        return dto;
    }

    private TripPlanDto toTripPlanDto(TripPlanEntity entity,
            List<String> participantIds) {
        TripPlanDto dto = new TripPlanDto();
        dto.id = entity.id;
        dto.groupId = entity.groupId;
        dto.title = entity.title;
        dto.description = entity.description;
        dto.coverImageUrl = entity.coverImageUrl;
        dto.startAt = formatInstant(entity.startAt);
        dto.endAt = formatInstant(entity.endAt);
        dto.participantIds = participantIds;
        dto.serverVersion = entity.serverVersion;
        dto.deleted = entity.deleted;
        return dto;
    }

    private List<String> mapParticipantIds(List<TripMemberCrossRef> members) {
        List<String> participantIds = new ArrayList<>();
        if (members == null || members.isEmpty()) {
            return participantIds;
        }
        for (TripMemberCrossRef member : members) {
            if (member != null && member.userId != null
                    && !member.userId.trim().isEmpty()) {
                participantIds.add(member.userId.trim());
            }
        }
        return participantIds;
    }

    private String formatInstant(long value) {
        if (value <= 0L) {
            return null;
        }
        try {
            return java.time.Instant.ofEpochMilli(value).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidLocalPath(String path) {
        return path != null && !path.trim().isEmpty()
                && new File(path).exists();
    }

    private boolean deleteLocalFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File file = new File(path);
        return !file.exists() || file.delete();
    }

    private boolean shouldStopRetrying() {
        return getRunAttemptCount() >= MAX_RUN_ATTEMPTS;
    }

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(
                ImageUploadWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        RETRY_BACKOFF_SECONDS,
                        TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request);
    }

    public static void schedulePeriodic(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ImageUploadWorker.class,
                15,
                TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }

    private static class UploadResult {
        boolean success;
        String remoteUrl;
        String errorMessage;
    }
}


