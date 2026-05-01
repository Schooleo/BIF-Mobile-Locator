package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.mapper.ReviewMapper;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.google.gson.Gson;

import java.util.List;

public class ReviewSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "ReviewSyncHandler";

    private final ReviewDao reviewDao;
    private final PlaceDao placeDao;
    private final SyncQueueDao syncQueueDao;
    private final AppDatabase appDatabase;
    private final Gson gson;

    public ReviewSyncEntityHandler(ReviewDao reviewDao,
                                   PlaceDao placeDao,
                                   SyncQueueDao syncQueueDao,
                                   AppDatabase appDatabase,
                                   Gson gson) {
        this.reviewDao = reviewDao;
        this.placeDao = placeDao;
        this.syncQueueDao = syncQueueDao;
        this.appDatabase = appDatabase;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "review";
    }

    @Override
    public String serializePayload(Object payload) {
        return gson.toJson(payload);
    }

    @Override
    public void applyPulledChange(SyncChangeDto change, String activeUserId) {
        try {
            String reviewId = change.entityId;

            if ("DELETE".equalsIgnoreCase(change.operation)) {
                if (!isBlank(reviewId)) {
                    appDatabase.runInTransaction(() -> {
                        ReviewEntity existing = reviewDao.getById(reviewId);
                        if (existing != null) {
                            existing.deleted = true;
                            existing.serverVersion = change.serverVersion;
                            existing.pendingSync = false;
                            reviewDao.upsert(existing);
                            updateCachedPlaceRating(existing.placeId, activeUserId);
                        }
                        syncQueueDao.removeByEntity("review", reviewId);
                    });
                }
                return;
            }

            if (change.payload == null || change.payload.isEmpty()) {
                return;
            }

            PlaceReviewDto dto = gson.fromJson(change.payload, PlaceReviewDto.class);
            if (dto == null) {
                return;
            }

            String resolvedPlaceId = !isBlank(dto.placeId)
                    ? dto.placeId.trim()
                    : null;
            String reviewUserId = !isBlank(dto.userId)
                    ? dto.userId.trim()
                    : null;

            if (isBlank(resolvedPlaceId) || isBlank(reviewUserId)) {
                return;
            }

            ReviewEntity existing = reviewDao.getById(reviewId);
            String originalPlaceId = existing != null ? existing.placeId : resolvedPlaceId;
            boolean identityCorrected = !resolvedPlaceId.equals(originalPlaceId);

            String finalOriginalPlaceId = originalPlaceId;
            appDatabase.runInTransaction(() -> {
                if (identityCorrected && existing != null) {
                    reviewDao.deleteByPlaceAndUserId(finalOriginalPlaceId, reviewUserId);
                }

                ReviewEntity entity = ReviewMapper.fromDto(dto, resolvedPlaceId);
                entity.id = reviewId;
                entity.serverVersion = change.serverVersion;
                entity.deleted = false;
                entity.pendingSync = false;
                reviewDao.upsert(entity);

                syncQueueDao.removeByEntity("review", reviewId);

                if (identityCorrected) {
                    Log.w(TAG, "Identity correction from sync pull. oldPlaceId="
                            + finalOriginalPlaceId + " newPlaceId=" + resolvedPlaceId);
                    updateCachedPlaceRating(finalOriginalPlaceId, activeUserId);
                }
                updateCachedPlaceRating(resolvedPlaceId, activeUserId);
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply pulled change for review", e);
        }
    }

    private void updateCachedPlaceRating(String placeId, String activeUserId) {
        if (isBlank(placeId) || isBlank(activeUserId)) {
            return;
        }

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

        PlaceEntity place = placeDao.getByIdSync(placeId, activeUserId);
        if (place == null) {
            return;
        }

        place.rating = count > 0 ? (double) totalStars / count : 0.0;
        placeDao.upsert(place);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
