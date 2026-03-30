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
    @Query("SELECT * FROM friendships WHERE id = :id LIMIT 1")
    FriendshipEntity getById(int id);

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnore(FriendshipEntity friendship);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FriendshipEntity> friendships);

    @Update
    void update(FriendshipEntity friendship);

    @Query("DELETE FROM friendships")
    void clearAll();

    @Query("DELETE FROM friendships WHERE ((requesterId = :userA AND receiverId = :userB) OR (requesterId = :userB AND receiverId = :userA))")
    void deleteBetweenUsers(String userA, String userB);

    @Transaction
    default void replaceAll(List<FriendshipEntity> friendships) {
        clearAll();
        if (friendships != null && !friendships.isEmpty()) {
            insertAll(friendships);
        }
    }

    @Transaction
    default boolean reservePendingIfAbsent(String requesterId, String receiverId, long nowMillis) {
        FriendshipEntity existing = findBetweenUsers(requesterId, receiverId);
        if (existing != null) {
            return false;
        }

        FriendshipEntity pending = new FriendshipEntity();
        pending.requesterId = requesterId;
        pending.receiverId = receiverId;
        pending.status = FriendshipStatus.PENDING;
        pending.createdAt = nowMillis;
        pending.updatedAt = nowMillis;
        return insertIgnore(pending) != -1L;
    }

    @Transaction
    default void rollbackReservedPending(String requesterId, String receiverId) {
        deleteBetweenUsers(requesterId, receiverId);
    }
}