package com.bif.app.data.source.local;

import androidx.room.TypeConverter;

import com.bif.app.data.source.local.entity.UploadStatus;

public class UploadStatusConverter {

    @TypeConverter
    public static String toRaw(UploadStatus status) {
        return status != null ? status.name() : null;
    }

    @TypeConverter
    public static UploadStatus fromRaw(String value) {
        if (value == null || value.trim().isEmpty()) {
            return UploadStatus.SYNCED;
        }
        try {
            return UploadStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return UploadStatus.SYNCED;
        }
    }
}
