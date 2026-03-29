package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.bif.app.data.source.local.entity.FriendEntity;

import java.util.List;

@Dao
public interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY name ASC")
    LiveData<List<FriendEntity>> getAllFriends();

    @Query("SELECT * FROM friends ORDER BY name ASC")
    List<FriendEntity> getAllFriendsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FriendEntity friend);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FriendEntity> friends);

    @Delete
    void delete(FriendEntity friend);

    @Query("DELETE FROM friends")
    void clearAll();

    @Transaction
    default void replaceAll(List<FriendEntity> friends) {
        clearAll();
        if (friends != null && !friends.isEmpty()) {
            insertAll(friends);
        }
    }
}
