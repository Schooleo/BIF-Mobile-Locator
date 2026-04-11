package com.bif.app.core.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.snackbar.Snackbar;

public final class AppSnackbar {

    private AppSnackbar() {
    }

    public static void show(@Nullable Context context, @StringRes int messageResId) {
        if (context == null) {
            return;
        }
        showInternal(context, context.getString(messageResId), Snackbar.LENGTH_SHORT);
    }

    public static void show(@Nullable Context context, @Nullable CharSequence message) {
        showInternal(context, message, Snackbar.LENGTH_SHORT);
    }

    public static void showLong(@Nullable Context context, @StringRes int messageResId) {
        if (context == null) {
            return;
        }
        showInternal(context, context.getString(messageResId), Snackbar.LENGTH_LONG);
    }

    public static void showLong(@Nullable Context context, @Nullable CharSequence message) {
        showInternal(context, message, Snackbar.LENGTH_LONG);
    }

    private static void showInternal(@Nullable Context context,
            @Nullable CharSequence message,
            int duration) {
        if (context == null || message == null || message.length() == 0) {
            return;
        }

        Activity activity = findActivity(context);
        if (activity == null) {
            return;
        }

        View anchor = activity.findViewById(android.R.id.content);
        if (anchor == null) {
            return;
        }

        Snackbar snackbar = Snackbar.make(anchor, message, duration);
        int backgroundColor = MaterialColors.getColor(anchor,
                com.google.android.material.R.attr.colorSurface);
        int textColor = MaterialColors.getColor(anchor,
                com.google.android.material.R.attr.colorOnSurface);
        snackbar.setBackgroundTint(backgroundColor);
        snackbar.setTextColor(textColor);
        snackbar.show();
    }

    @Nullable
    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }
}
