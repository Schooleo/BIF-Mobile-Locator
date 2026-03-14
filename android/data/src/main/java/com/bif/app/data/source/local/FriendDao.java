package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.FriendEntity;

import java.util.List;

@Dao
public interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY name ASC")
    LiveData<List<FriendEntity>> getAllFriends();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FriendEntity friend);

    @Delete
    void delete(FriendEntity friend);
}
