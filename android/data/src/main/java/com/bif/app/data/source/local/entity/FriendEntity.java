package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "friends")
public class FriendEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String serverUserId;

    public String name;
    public String avatarLetter;
    public int avatarColor;
    public boolean isOnline;
}
