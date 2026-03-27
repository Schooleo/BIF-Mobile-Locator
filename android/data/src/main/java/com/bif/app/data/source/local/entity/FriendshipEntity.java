package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "friendships",
        indices = {
                @Index(value = {"requesterId"}),
                @Index(value = {"receiverId"}),
                @Index(value = {"requesterId", "receiverId"}, unique = true)
        }
)
public class FriendshipEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String serverId;
    public String requesterId;
    public String requesterName;
    public String receiverId;
    public FriendshipStatus status;
    public long createdAt;
    public long updatedAt;
}