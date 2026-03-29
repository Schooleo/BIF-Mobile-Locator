package com.bif.app.data.source.local;

import androidx.room.TypeConverter;

import com.bif.app.data.source.local.entity.FriendshipStatus;

public class FriendshipStatusConverter {
    @TypeConverter
    public static String fromStatus(FriendshipStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static FriendshipStatus toStatus(String value) {
        return value == null ? null : FriendshipStatus.valueOf(value);
    }
}