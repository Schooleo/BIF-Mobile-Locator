package com.bif.app.data.source.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.ProfileEntity;

@Dao
public interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE userId = :userId AND deleted = 0 LIMIT 1")
    ProfileEntity getActiveByUserId(String userId);

    @Query("SELECT * FROM profiles WHERE userId = :userId LIMIT 1")
    ProfileEntity getByUserId(String userId);

        @Query("SELECT * FROM profiles WHERE deleted = 0 AND localImagePath IS NOT NULL "
            + "AND uploadStatus IN ('PENDING','ERROR') ORDER BY updatedAt ASC LIMIT 1")
        ProfileEntity getFirstPendingUpload();

        @Query("SELECT * FROM profiles WHERE deleted = 0 AND uploadStatus = 'SYNCED' "
            + "AND localImagePath IS NOT NULL")
        java.util.List<ProfileEntity> getSyncedWithLocalImagePath();

        @Query("SELECT localImagePath FROM profiles WHERE localImagePath IS NOT NULL")
        java.util.List<String> getAllReferencedLocalImagePaths();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ProfileEntity profile);

    @Query("DELETE FROM profiles WHERE userId = :userId")
    void deleteByUserId(String userId);
}
