package com.bif.app.core.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class ChatReadStateStore {

    private static final String PREF_NAME = "CHAT_READ_STATE";
    private static final String KEY_PREFIX_GROUP_LAST_READ = "group_last_read_";

    private ChatReadStateStore() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static long getGroupLastReadAt(Context context, String groupId) {
        if (context == null || groupId == null || groupId.trim().isEmpty()) {
            return 0L;
        }
        return getPrefs(context).getLong(KEY_PREFIX_GROUP_LAST_READ + groupId, 0L);
    }

    public static void markGroupReadAt(Context context, String groupId, long timestampMs) {
        if (context == null || groupId == null || groupId.trim().isEmpty()) {
            return;
        }
        getPrefs(context).edit()
                .putLong(KEY_PREFIX_GROUP_LAST_READ + groupId, Math.max(0L, timestampMs))
                .apply();
    }

    public static void markGroupReadNow(Context context, String groupId) {
        markGroupReadAt(context, groupId, System.currentTimeMillis());
    }
}
