package com.bif.app.data.source.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;

import java.util.List;

@Dao
public interface FriendshipDao {
    @Query("SELECT * FROM friendships WHERE status = :status ORDER BY updatedAt DESC")
    List<FriendshipEntity> getByStatus(FriendshipStatus status);

    @Query("SELECT * FROM friendships WHERE requesterId = :userId OR receiverId = :userId ORDER BY updatedAt DESC")
    List<FriendshipEntity> getByUserId(String userId);

    @Query("SELECT * FROM friendships WHERE ((requesterId = :userA AND receiverId = :userB) OR (requesterId = :userB AND receiverId = :userA)) LIMIT 1")
    FriendshipEntity findBetweenUsers(String userA, String userB);

    @Query("SELECT * FROM friendships WHERE (requesterId = :userId OR receiverId = :userId) AND status = :status ORDER BY updatedAt DESC")
    List<FriendshipEntity> getByUserIdAndStatus(String userId, FriendshipStatus status);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FriendshipEntity friendship);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FriendshipEntity> friendships);

    @Update
    void update(FriendshipEntity friendship);

    @Query("DELETE FROM friendships")
    void clearAll();

    @Transaction
    default void replaceAll(List<FriendshipEntity> friendships) {
        clearAll();
        if (friendships != null && !friendships.isEmpty()) {
            insertAll(friendships);
        }
    }
}