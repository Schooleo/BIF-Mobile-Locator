package com.bif.app.data.repository;

import android.content.Context;
import android.util.Log;

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
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IReviewRepository;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    }

    /** Lấy userId hiện tại động theo từng request, không cache tĩnh */
    private String getActiveUserId() {
        String userId = UserPreferences.getUserId(appContext);
        if (userId == null || userId.trim().isEmpty()) return "anonymous";
        return userId.trim();
    }

    @Override
    public LiveData<List<Review>> getReviewsForPlace(String placeId) {
        return Transformations.map(reviewDao.getByPlaceId(placeId), ReviewMapper::toDomainList);
    }

    @Override
    public LiveData<Review> getMyReview(String placeId) {
        return Transformations.map(reviewDao.getReview(placeId, getActiveUserId()), ReviewMapper::toDomain);
    }

    @Override
    public void submitReview(String placeId, int stars, String comment) {
        executeReviewAction(placeId, stars, comment, "CREATE");
    }

    @Override
    public void updateReview(String placeId, int stars, String comment) {
        executeReviewAction(placeId, stars, comment, "UPDATE");
    }

    private void executeReviewAction(String placeId, int stars, String comment, String operation) {
        executorService.execute(() -> {
            String userId = getActiveUserId();
            Review review = new Review();
            review.placeId = placeId;
            review.userId = userId;
            review.userName = "Me";
            review.stars = stars;
            review.comment = comment;
            review.createdAt = System.currentTimeMillis();
            review.pendingSync = true;
            review.deleted = false;

            appDatabase.runInTransaction(() -> {
                ReviewEntity entity = ReviewMapper.toEntity(review);
                reviewDao.upsert(entity);

                SyncQueueEntity syncEntry = createSyncEntry(
                        "review",
                        placeId + ":" + userId,
                        operation,
                        ReviewMapper.toDto(review)
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void deleteMyReview(String placeId) {
        executorService.execute(() -> {
            String userId = getActiveUserId();
            appDatabase.runInTransaction(() -> {
                ReviewEntity existing = reviewDao.getReviewSync(placeId, userId);
                if (existing != null) {
                    existing.deleted = true;
                    existing.pendingSync = true;
                    reviewDao.upsert(existing);
                }

                SyncQueueEntity syncEntry = createSyncEntry(
                        "review",
                         placeId + ":" + userId,
                        "DELETE",
                        null
                );
                syncQueueDao.enqueue(syncEntry);
            });
            syncManager.syncIfOnline();
        });
    }

    @Override
    public void refreshReviews(String placeId) {
        executorService.execute(() -> {
            try {
                Response<List<PlaceReviewDto>> response = restApiService.getPlaceReviews(placeId).execute();
                if (response.isSuccessful() && response.body() != null) {
                    appDatabase.runInTransaction(() -> {
                        for (PlaceReviewDto dto : response.body()) {
                            ReviewEntity entity = ReviewMapper.fromDto(dto, placeId);
                            // Avoid overwriting a locally modified pending item
                            ReviewEntity local = reviewDao.getReviewSync(placeId, dto.userId);
                            if (local == null || !local.pendingSync) {
                                reviewDao.upsert(entity);
                            }
                        }
                    });
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to fetch reviews for place " + placeId, e);
            }
        });
    }

    @Override
    public String resolveInternalPlaceId(String externalSource, String externalId, double lat, double lng, String name) {
        try {
            PlaceResolveRequestDto request = new PlaceResolveRequestDto();
            request.externalSource = externalSource;
            request.externalId = externalId;
            request.lat = lat;
            request.lng = lng;
            request.name = name;

            Response<PlaceResolveResponseDto> res = restApiService.resolvePlace(request).execute();
            if (res.isSuccessful() && res.body() != null) {
                return res.body().internalPlaceId;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to resolve placeId", e);
        }
        return UUID.randomUUID().toString(); // Fallback isolated offline case
    }

    private SyncQueueEntity createSyncEntry(String entityType, String entityId, String operation, Object payload) {
        SyncQueueEntity entry = new SyncQueueEntity();
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

    private String resolveActiveUserId(Context context) {
        if (context == null) return "anonymous";
        String userId = UserPreferences.getUserId(context);
        if (userId == null || userId.trim().isEmpty()) return "anonymous";
        return userId.trim();
    }
}
