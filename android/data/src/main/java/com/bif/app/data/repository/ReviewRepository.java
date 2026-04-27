package com.bif.app.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.place.PlaceResolveRequestDto;
import com.bif.app.core.network.dto.place.PlaceResolveResponseDto;
import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.ReviewMapper;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.PlaceIdentityContext;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IReviewRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class ReviewRepository implements IReviewRepository {

    private static final String TAG = "ReviewRepository";

    private final ReviewDao reviewDao;
    private final PlaceDao placeDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final SyncManager syncManager;
    private final RestApiService restApiService;
    private final ExecutorService executorService;
    private final Gson gson;
    private final Context appContext;
    private final androidx.lifecycle.MutableLiveData<String> activeUserIdLiveData = new androidx.lifecycle.MutableLiveData<>();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefListener = (prefs, key) -> {
        if (UserPreferences.KEY_USER_ID.equals(key)) {
            activeUserIdLiveData.postValue(getActiveUserId());
        }
    };

    @Inject
    public ReviewRepository(ReviewDao reviewDao,
                            PlaceDao placeDao,
                            SyncQueueDao syncQueueDao,
                            AppDatabase appDatabase,
                            SyncManager syncManager,
                            RestApiService restApiService,
                            ExecutorService executorService,
                            @ApplicationContext Context appContext) {
        this.reviewDao = reviewDao;
        this.placeDao = placeDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.syncManager = syncManager;
        this.restApiService = restApiService;
        this.executorService = executorService;
        this.gson = new Gson();
        this.appContext = appContext;
        this.activeUserIdLiveData.setValue(getActiveUserId());
        this.appContext.getSharedPreferences(UserPreferences.PREF_NAME, Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefListener);
    }

    /** Lấy userId hiện tại động theo từng request, không cache tĩnh */
    private String getActiveUserId() {
        String userId = UserPreferences.getUserId(appContext);
        if (userId == null || userId.trim().isEmpty()) {
            userId = UserPreferences.getId(appContext);
        }
        if (userId == null || userId.trim().isEmpty()) return "anonymous";
        return userId.trim();
    }

    @Override
    public LiveData<List<Review>> getReviewsForPlace(String placeId) {
        return Transformations.map(reviewDao.getByPlaceId(placeId), ReviewMapper::toDomainList);
    }

    @Override
    public LiveData<Review> getMyReview(String placeId) {
        return Transformations.switchMap(activeUserIdLiveData, userId ->
                Transformations.map(reviewDao.getReview(placeId, userId), ReviewMapper::toDomain));
    }

    @Override
    public void submitReview(String placeId,
                             int stars,
                             String comment,
                     @Nullable PlaceIdentityContext identityContext) {
        String captureUserId = getActiveUserId();
        executeReviewAction(
                placeId,
                stars,
                comment,
                "CREATE",
                captureUserId,
                getActiveUsername(),
                identityContext);
    }

    @Override
    public void updateReview(String placeId,
                             int stars,
                             String comment,
                     @Nullable PlaceIdentityContext identityContext) {
        String captureUserId = getActiveUserId();
        executeReviewAction(
                placeId,
                stars,
                comment,
                "UPDATE",
                captureUserId,
                getActiveUsername(),
                identityContext);
    }

    private void executeReviewAction(String placeId,
                                     int stars,
                                     String comment,
                                     String operation,
                                     String userId,
                                     String userName,
                                     @Nullable PlaceIdentityContext identityContext) {
        executorService.execute(() -> {
            Review review = new Review();
            review.placeId = placeId;
            review.userId = userId;
            review.userName = userName;
            review.stars = stars;
            review.comment = comment;
            if (identityContext != null) {
                review.externalSource = identityContext.externalSource;
                review.externalId = identityContext.externalId;
                review.lat = identityContext.lat;
                review.lng = identityContext.lng;
                review.placeName = identityContext.placeName;
            }
            review.pendingSync = true;
            review.deleted = false;

            if (identityContext != null
                    && ((identityContext.lat != null && identityContext.lat == 0.0d)
                    || (identityContext.lng != null && identityContext.lng == 0.0d))) {
                Log.w(TAG, "Review metadata has suspicious coordinates (0.0). placeId=" + placeId);
            }

            ReviewEntity existing = reviewDao.getReviewSync(placeId, userId);
            final long createdAt = existing != null
                    ? existing.createdAt
                    : System.currentTimeMillis();
            if (existing != null) {
                if (review.externalSource == null || review.externalSource.trim().isEmpty()) {
                    review.externalSource = existing.externalSource;
                }
                if (review.externalId == null || review.externalId.trim().isEmpty()) {
                    review.externalId = existing.externalId;
                }
                if (review.placeName == null || review.placeName.trim().isEmpty()) {
                    review.placeName = existing.placeName;
                }
                if ((review.lat == null || review.lat == 0.0d) && existing.lat != null) {
                    review.lat = existing.lat;
                }
                if ((review.lng == null || review.lng == 0.0d) && existing.lng != null) {
                    review.lng = existing.lng;
                }
            }

            boolean syncedOnline = syncReviewOnline(placeId, review, operation);
            if (!syncedOnline) {
                appDatabase.runInTransaction(() -> {
                    review.createdAt = createdAt;
                    ReviewEntity entity = ReviewMapper.toEntity(review);
                    reviewDao.upsert(entity);
                    updateCachedPlaceRating(placeId);

                    SyncQueueEntity syncEntry = createSyncEntry(
                            "review",
                            placeId + ":" + userId,
                            operation,
                            ReviewMapper.toDto(review),
                            userId
                    );
                    syncQueueDao.enqueue(syncEntry);
                });
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void deleteMyReview(String placeId) {
        executorService.execute(() -> {
            String userId = getActiveUserId();
            ReviewEntity existing = reviewDao.getReviewSync(placeId, userId);
            appDatabase.runInTransaction(() -> {
                if (existing != null) {
                    existing.deleted = true;
                    existing.pendingSync = true;
                    reviewDao.upsert(existing);
                }
                updateCachedPlaceRating(placeId);
            });

            boolean deletedOnline = deleteReviewOnline(placeId, userId);
            if (!deletedOnline) {
                appDatabase.runInTransaction(() -> {
                    SyncQueueEntity syncEntry = createSyncEntry(
                            "review",
                            placeId + ":" + userId,
                            "DELETE",
                            null,
                            userId
                    );
                    syncQueueDao.enqueue(syncEntry);
                });
                syncManager.syncIfOnline();
            }
        });
    }

    @Override
    public void refreshReviews(String placeId) {
        refreshReviews(placeId, null);
    }

    @Override
    public void refreshReviews(String placeId, Runnable onComplete) {
        executorService.execute(() -> {
            try {
                Response<List<PlaceReviewDto>> response = restApiService.getPlaceReviews(placeId).execute();
                if (response.isSuccessful() && response.body() != null) {
                    List<PlaceReviewDto> serverReviews = response.body();
                    appDatabase.runInTransaction(() -> {
                        Set<String> serverUserIds = new HashSet<>();
                        Set<String> affectedPlaceIds = new HashSet<>();
                        affectedPlaceIds.add(placeId);

                        for (PlaceReviewDto dto : serverReviews) {
                            if (dto == null || dto.userId == null || dto.userId.trim().isEmpty()) {
                                continue;
                            }

                            String serverUserId = dto.userId.trim();
                            serverUserIds.add(serverUserId);
                            String resolvedPlaceId = normalizeServerPlaceId(dto.placeId, placeId);
                            boolean identityCorrected = !resolvedPlaceId.equals(placeId);
                            affectedPlaceIds.add(resolvedPlaceId);

                            ReviewEntity entity = ReviewMapper.fromDto(dto, resolvedPlaceId);
                            // Avoid overwriting a locally modified pending item
                            ReviewEntity local = reviewDao.getReviewSync(resolvedPlaceId, serverUserId);
                            if (identityCorrected) {
                                reviewDao.deleteByPlaceAndUserId(placeId, serverUserId);
                                syncQueueDao.removeByEntity("review", placeId + ":" + serverUserId);
                            }
                            if (local == null || !local.pendingSync) {
                                reviewDao.upsert(entity);
                            }
                        }

                        List<ReviewEntity> localReviews = reviewDao.getByPlaceIdSync(placeId);
                        if (localReviews == null) {
                            return;
                        }

                        for (ReviewEntity localReview : localReviews) {
                            if (localReview == null || localReview.pendingSync) {
                                continue;
                            }
                            if (localReview.userId == null || localReview.userId.trim().isEmpty()) {
                                continue;
                            }

                            String localUserId = localReview.userId.trim();
                            if (!serverUserIds.contains(localUserId)) {
                                reviewDao.deleteByPlaceAndUserId(placeId, localUserId);
                            }
                        }

                        for (String affectedPlaceId : affectedPlaceIds) {
                            updateCachedPlaceRating(affectedPlaceId);
                        }
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to fetch reviews for place " + placeId, e);
            } finally {
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Exception callbackError) {
                        Log.e(TAG, "refreshReviews completion callback failed", callbackError);
                    }
                }
            }
        });
    }

    @Override
    public String resolveInternalPlaceId(String externalSource, String externalId, double lat, double lng, String name) {
        if (isBlank(externalSource)
                || isBlank(externalId)
                || isBlank(name)
                || Place.SOURCE_PREVIEW.equalsIgnoreCase(externalSource.trim())) {
            Log.w(TAG, "resolveInternalPlaceId skipped remote resolve due to invalid or preview metadata"
                    + " source=" + externalSource
                    + " externalId=" + externalId
                    + " name=" + name);
            return buildDeterministicFallbackPlaceId(externalSource, externalId, lat, lng, name);
        }

        try {

            PlaceResolveRequestDto request = new PlaceResolveRequestDto();
            request.externalSource = externalSource;
            request.externalId = externalId;
            request.lat = lat;
            request.lng = lng;
            request.name = name;

            Response<PlaceResolveResponseDto> res = restApiService.resolvePlace(request).execute();

            if (res.isSuccessful() && res.body() != null) {
                String internalId = res.body().internalPlaceId;
                if (internalId != null && !internalId.trim().isEmpty()) {
                    return internalId;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to resolve placeId", e);
        }
        return buildDeterministicFallbackPlaceId(externalSource, externalId, lat, lng, name);
    }

        private String buildDeterministicFallbackPlaceId(String externalSource,
                                 String externalId,
                                 double lat,
                                 double lng,
                                 String name) {
        String source = externalSource == null
            ? ""
            : externalSource.trim().toLowerCase(Locale.ROOT);
        String extId = externalId == null
            ? ""
            : externalId.trim().toLowerCase(Locale.ROOT);
        String placeName = name == null
            ? ""
            : name.trim().toLowerCase(Locale.ROOT);
        String seed = source + "|" + extId + "|" + lat + "|" + lng + "|" + placeName;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        }

    private String getActiveUsername() {
        String username = UserPreferences.getUsername(appContext);
        if (username == null || username.trim().isEmpty()) {
            return "Me";
        }
        return username.trim();
    }

    private boolean syncReviewOnline(String placeId, Review review, String operation) {
        if (restApiService == null || !syncManager.isOnline()) {
            return false;
        }

        try {
            PlaceReviewDto payload = ReviewMapper.toDto(review);
            Response<PlaceReviewDto> response;
            if ("UPDATE".equalsIgnoreCase(operation)) {
                response = restApiService.updateMyReview(placeId, payload).execute();
            } else {
                response = restApiService.addReview(placeId, payload).execute();
                if (response.code() == 409) {
                    response = restApiService.updateMyReview(placeId, payload).execute();
                }
            }

            if (!response.isSuccessful() || response.body() == null) {
                Log.w(TAG, "Review write-through failed. code=" + response.code()
                        + " operation=" + operation + " placeId=" + placeId);
                return false;
            }

            PlaceReviewDto dto = response.body();
            String localPlaceId = review.placeId;
            if (isBlank(localPlaceId)) {
                localPlaceId = placeId;
            }
            String reviewUserId = review.userId;
            if (isBlank(reviewUserId)) {
                reviewUserId = getActiveUserId();
            }
            persistServerReviewWithHealing(localPlaceId, reviewUserId, dto);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Review write-through failed for place " + placeId, e);
            return false;
        }
    }

    private boolean deleteReviewOnline(String placeId, String userId) {
        if (restApiService == null || !syncManager.isOnline()) {
            return false;
        }

        try {
            Response<Void> response = restApiService.deleteMyReview(placeId).execute();
            if (!response.isSuccessful() && response.code() != 404) {
                Log.w(TAG, "Review delete write-through failed. code="
                        + response.code() + " placeId=" + placeId);
                return false;
            }

            appDatabase.runInTransaction(() -> {
                reviewDao.deleteByPlaceAndUserId(placeId, userId);
                syncQueueDao.removeByEntity("review", placeId + ":" + userId);
                updateCachedPlaceRating(placeId);
            });
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Review delete write-through failed for place " + placeId, e);
            return false;
        }
    }

    private void updateCachedPlaceRating(String placeId) {
        List<ReviewEntity> localReviews = reviewDao.getByPlaceIdSync(placeId);
        if (localReviews == null) {
            return;
        }

        int count = 0;
        int totalStars = 0;
        for (ReviewEntity review : localReviews) {
            if (review == null || review.deleted) {
                continue;
            }
            count++;
            totalStars += review.stars;
        }

        PlaceEntity place = placeDao.getByIdSync(placeId, getActiveUserId());
        if (place == null) {
            return;
        }

        place.rating = count > 0 ? (double) totalStars / count : 0.0;
        placeDao.upsert(place);
    }

    private void persistServerReviewWithHealing(String localPlaceId,
                                                String userId,
                                                PlaceReviewDto dto) {
        String normalizedLocalPlaceId = normalizeServerPlaceId(localPlaceId, localPlaceId);
        String normalizedUserId = userId;
        if (isBlank(normalizedUserId) && dto != null && !isBlank(dto.userId)) {
            normalizedUserId = dto.userId.trim();
        }
        if (isBlank(normalizedUserId)) {
            normalizedUserId = getActiveUserId();
        }

        String resolvedPlaceId = normalizeServerPlaceId(dto != null ? dto.placeId : null,
                normalizedLocalPlaceId);
        boolean identityCorrected = !resolvedPlaceId.equals(normalizedLocalPlaceId);
        String finalNormalizedLocalPlaceId = normalizedLocalPlaceId;
        String finalNormalizedUserId = normalizedUserId;
        String finalResolvedPlaceId = resolvedPlaceId;
        boolean finalIdentityCorrected = identityCorrected;

        appDatabase.runInTransaction(() -> {
            if (finalIdentityCorrected) {
                reviewDao.deleteByPlaceAndUserId(finalNormalizedLocalPlaceId, finalNormalizedUserId);
            }

            ReviewEntity entity = ReviewMapper.fromDto(dto, finalResolvedPlaceId);
            entity.pendingSync = false;
            entity.deleted = false;
            reviewDao.upsert(entity);

            syncQueueDao.removeByEntity("review", finalNormalizedLocalPlaceId + ":" + finalNormalizedUserId);

            if (finalIdentityCorrected) {
                updateCachedPlaceRating(finalNormalizedLocalPlaceId);
                Log.w(TAG, "Identity correction detected. oldPlaceId="
                        + finalNormalizedLocalPlaceId + " newPlaceId=" + finalResolvedPlaceId);
            }
            updateCachedPlaceRating(finalResolvedPlaceId);
        });
    }

    private String normalizeServerPlaceId(String serverPlaceId, String fallbackPlaceId) {
        if (serverPlaceId != null && !serverPlaceId.trim().isEmpty()) {
            return serverPlaceId.trim();
        }
        if (fallbackPlaceId != null && !fallbackPlaceId.trim().isEmpty()) {
            return fallbackPlaceId.trim();
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private SyncQueueEntity createSyncEntry(String entityType,
            String entityId,
            String operation,
            Object payload,
            String userId) {
        SyncQueueEntity entry = new SyncQueueEntity();
        entry.userId = userId != null ? userId : "";
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.operation = operation;
        entry.clientChangeId = UUID.randomUUID().toString();
        entry.payload = payload != null ? gson.toJson(payload) : null;
        entry.status = "PENDING";
        entry.retryCount = 0;
        entry.createdAt = System.currentTimeMillis();
        return entry;
    }
}
