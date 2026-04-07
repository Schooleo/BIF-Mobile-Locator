package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.mapper.ReviewMapper;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.google.gson.Gson;

public class ReviewSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "ReviewSyncHandler";
    private final ReviewDao reviewDao;
    private final Gson gson;

    public ReviewSyncEntityHandler(ReviewDao reviewDao, Gson gson) {
        this.reviewDao = reviewDao;
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
            if ("DELETE".equalsIgnoreCase(change.operation)) {
                String[] parts = change.entityId.split(":");
                if (parts.length == 2) {
                    ReviewEntity existing = reviewDao.getReviewSync(parts[0], parts[1]);
                    if (existing != null) {
                        existing.deleted = true;
                        existing.serverVersion = change.serverVersion;
                        existing.pendingSync = false;
                        reviewDao.upsert(existing);
                    }
                }
                return;
            }

            if (change.payload == null || change.payload.isEmpty()) return;

            PlaceReviewDto dto = gson.fromJson(change.payload, PlaceReviewDto.class);
            if (dto == null || dto.userId == null) return;

            String placeId = dto.placeId;
            if (placeId == null) {
                String[] parts = change.entityId.split(":");
                if (parts.length == 2) {
                    placeId = parts[0];
                }
            }

            if (placeId == null) return;

            ReviewEntity entity = ReviewMapper.fromDto(dto, placeId);
            entity.serverVersion = change.serverVersion;
            entity.deleted = false;
            entity.pendingSync = false;

            reviewDao.upsert(entity);

        } catch (Exception e) {
            Log.e(TAG, "Failed to apply pulled change for review", e);
        }
    }
}
