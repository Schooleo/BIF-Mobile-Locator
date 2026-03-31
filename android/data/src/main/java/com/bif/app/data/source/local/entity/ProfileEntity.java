package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "profiles")
public class ProfileEntity {
    @NonNull
    @PrimaryKey
    public String userId;

    public String displayName;
    public String email;
    public String avatarLetter;
    public int avatarColor;

    // Sync fields
    public long serverVersion;
    public long updatedAt;
    public boolean deleted;
}
