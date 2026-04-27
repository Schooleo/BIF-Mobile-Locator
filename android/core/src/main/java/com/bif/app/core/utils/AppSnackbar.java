package com.bif.app.core.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        if (context == null || message == null) {
            return;
        }

        String displayMessage = message.toString().trim();
        if (displayMessage.isEmpty()) {
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

        Snackbar snackbar = Snackbar.make(anchor, displayMessage, duration);
        View bottomNavigation = findBottomNavigation(activity);
        if (bottomNavigation != null) {
            snackbar.setAnchorView(bottomNavigation);
        }

        View snackbarView = snackbar.getView();
        ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) params;
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            layoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            int horizontalMargin = dpToPx(activity, 16);
            int keyboardInset = getKeyboardInset(anchor);
            layoutParams.leftMargin = horizontalMargin;
            layoutParams.rightMargin = horizontalMargin;
            layoutParams.bottomMargin = dpToPx(activity, 12) + keyboardInset;
            snackbarView.setLayoutParams(layoutParams);
        } else if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
            int horizontalMargin = dpToPx(activity, 16);
            int keyboardInset = getKeyboardInset(anchor);
            marginParams.leftMargin = horizontalMargin;
            marginParams.rightMargin = horizontalMargin;
            marginParams.bottomMargin = dpToPx(activity, 12) + keyboardInset;
            snackbarView.setLayoutParams(marginParams);
        }

        snackbar.setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE);
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

    @Nullable
    private static View findBottomNavigation(@NonNull Activity activity) {
        int bottomNavId = activity.getResources().getIdentifier(
                "bottom_navigation",
                "id",
                activity.getPackageName());
        if (bottomNavId == 0) {
            return null;
        }
        return activity.findViewById(bottomNavId);
    }

    private static int dpToPx(@NonNull Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static int getKeyboardInset(@NonNull View anchor) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(anchor);
        if (insets == null) {
            return 0;
        }
        return insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
    }
}
