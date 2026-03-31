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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(ProfileEntity profile);

    @Query("DELETE FROM profiles WHERE userId = :userId")
    void deleteByUserId(String userId);
}
