package com.bif.app.data.source.local.entity;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import java.util.List;

public class GroupWithFriends {
    @Embedded
    public GroupEntity group;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = GroupFriendCrossRef.class,
                    parentColumn = "groupId",
                    entityColumn = "friendId"
            )
    )
    public List<FriendEntity> friends;
}