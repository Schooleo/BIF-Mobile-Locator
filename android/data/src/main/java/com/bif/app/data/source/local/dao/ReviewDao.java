package com.bif.app.data.source.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.ReviewEntity;

import java.util.List;

@Dao
public interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE placeId = :placeId AND deleted = 0 ORDER BY createdAt DESC")
    LiveData<List<ReviewEntity>> getByPlaceId(String placeId);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId AND userId = :userId LIMIT 1")
    ReviewEntity getReviewSync(String placeId, String userId);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId")
    List<ReviewEntity> getByPlaceIdSync(String placeId);

    @Query("SELECT * FROM reviews WHERE placeId = :placeId AND userId = :userId AND deleted = 0 LIMIT 1")
    LiveData<ReviewEntity> getReview(String placeId, String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ReviewEntity review);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<ReviewEntity> reviews);

    @Query("DELETE FROM reviews WHERE placeId = :placeId AND userId = :userId")
    void deleteByPlaceAndUserId(String placeId, String userId);

    @Query("SELECT * FROM reviews WHERE pendingSync = 1")
    List<ReviewEntity> getPendingSync();

    @Query("UPDATE reviews SET serverVersion = :version, lastSyncedAt = :timestamp, pendingSync = 0 WHERE placeId = :placeId AND userId = :userId")
    void markSynced(String placeId, String userId, long version, long timestamp);
}
