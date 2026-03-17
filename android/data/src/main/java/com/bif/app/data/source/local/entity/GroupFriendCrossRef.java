package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "group_friend_cross_ref",
        primaryKeys = {"groupId", "friendId"},
        foreignKeys = {
                @ForeignKey(
                        entity = GroupEntity.class,
                        parentColumns = "id",
                        childColumns = "groupId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = FriendEntity.class,
                        parentColumns = "id",
                        childColumns = "friendId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = "friendId")
        }
)
public class GroupFriendCrossRef {
    public int groupId;
    public int friendId;

    public GroupFriendCrossRef(int groupId, int friendId) {
        this.groupId = groupId;
        this.friendId = friendId;
    }
}