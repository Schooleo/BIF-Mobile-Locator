package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.GroupWithFriends;

import java.util.List;

@Dao
public interface GroupDao {
    @Transaction
    @Query("SELECT * FROM `groups`")
    LiveData<List<GroupWithFriends>> getAllGroupsWithFriends();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertGroup(GroupEntity group);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertGroupFriendCrossRefs(List<GroupFriendCrossRef> crossRefs);

    @Query("DELETE FROM `groups` WHERE id = :groupId")
    void deleteGroupById(int groupId);
}