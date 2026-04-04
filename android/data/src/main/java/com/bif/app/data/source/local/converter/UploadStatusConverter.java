package com.bif.app.data.source.local.converter;

import androidx.room.TypeConverter;
import android.util.Log;

import com.bif.app.data.source.local.entity.UploadStatus;

public class UploadStatusConverter {

    private static final String TAG = "UploadStatusConverter";

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
            Log.w(TAG, "Unknown upload status value: " + value + ". Falling back to SYNCED.");
            return UploadStatus.SYNCED;
        }
    }
}
