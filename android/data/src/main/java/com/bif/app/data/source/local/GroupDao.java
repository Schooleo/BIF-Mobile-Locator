package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.GroupWithFriends;

import java.util.List;

@Dao
public interface GroupDao {
    @Transaction
    @Query("SELECT * FROM `groups`")
    LiveData<List<GroupWithFriends>> getAllGroupsWithFriends();

    @Transaction
    @Query("SELECT * FROM `groups` WHERE id = :groupId")
    LiveData<GroupWithFriends> getGroupWithFriendsById(int groupId);

    @Query("SELECT * FROM `groups` WHERE serverId = :serverId LIMIT 1")
    LiveData<GroupEntity> getGroupByServerId(String serverId);

    @Query("SELECT * FROM `groups`")
    List<GroupEntity> getAllGroupsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(GroupEntity group);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertGroupFriendCrossRefs(List<GroupFriendCrossRef> crossRefs);

    @Update
    void updateGroup(GroupEntity group);

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    void deleteGroupById(int groupId);

    @Query("DELETE FROM `groups` WHERE serverId = :serverId")
    void deleteByServerId(String serverId);

    @Query("DELETE FROM `groups`")
    void clearGroups();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAllGroups(List<GroupEntity> groups);

    @Transaction
    default void replaceAllGroups(List<GroupEntity> groups) {
        clearGroups();
        if (groups != null && !groups.isEmpty()) {
            insertAllGroups(groups);
        }
    }

    @Query("DELETE FROM group_friend_cross_ref WHERE groupId = :groupId AND friendId = :friendId")
    void deleteGroupFriendCrossRef(int groupId, int friendId);
}