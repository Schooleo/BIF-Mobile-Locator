package com.bif.app.core.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogUtils {
    private static final String TAG = "DialogUtils";

    public interface TripSummary {
        String getDurationFormatted();
    }

    public interface OnActionClickListener {
        void onViewDetailClicked();

        void onCloseClicked();
    }

    public static void showConfirmDialog(Context context, String title, String message,
                                         String positiveText, String negativeText,
                                         Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context, com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveText, (dialog, which) -> {
                    if (onConfirm != null) {
                        onConfirm.run();
                    }
                })
                .setNegativeButton(negativeText, (dialog, which) -> dialog.dismiss())
                .show();
    }

    public static void showArrivedDialog(Context context,
                                         TripSummary summary,
                                         OnActionClickListener listener) {
        if (context == null) {
            return;
        }

        int layoutId = resolveResourceId(context, "layout", "dialog_trip_completed");
        if (layoutId == 0) {
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(layoutId, null);
        if (dialogView == null) {
            return;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(
                context,
                com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog
        )
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView durationText = findTextView(
                context,
                dialogView,
                "tv_trip_complete_duration");
        if (durationText != null && summary != null) {
            durationText.setText(summary.getDurationFormatted());
        }

        View btnViewDetail = findView(
                context,
                dialogView,
                "btn_trip_complete_view_destination");

        View btnClose = findView(
                context,
                dialogView,
                "btn_trip_complete_close");

        if (btnViewDetail != null) {
            btnViewDetail.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    listener.onViewDetailClicked();
                }
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                dialog.dismiss();
                if (listener != null) {
                    listener.onCloseClicked();
                }
            });
        }

        dialog.show();
    }

    private static int resolveResourceId(Context context, String resourceType, String resourceName) {
        return context.getResources().getIdentifier(resourceName, resourceType, context.getPackageName());
    }

    private static View findView(Context context, View root, String... idNames) {
        if (root == null || idNames == null) {
            return null;
        }

        for (String idName : idNames) {
            int id = resolveResourceId(context, "id", idName);
            if (id == 0) {
                continue;
            }
            View view = root.findViewById(id);
            if (view != null) {
                return view;
            }
        }
        return null;
    }

    private static TextView findTextView(Context context, View root, String... idNames) {
        View candidate = findView(context, root, idNames);
        return candidate instanceof TextView ? (TextView) candidate : null;
    }

    public interface DialogViewReadyListener {
        void onReady(View dialogView, androidx.appcompat.app.AlertDialog dialog);
    }

    public static void showCustomViewDialog(Context context, int layoutId,
                                            int closeBtnId,
                                            DialogViewReadyListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(layoutId, null);
        if (dialogView == null) return;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(
                context,
                com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog
        )
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (closeBtnId > 0) {
            View btnClose = dialogView.findViewById(closeBtnId);
            if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        if (listener != null) listener.onReady(dialogView, dialog);

        dialog.show();
    }

    public interface OnInputConfirmListener {
        void onConfirm(String inputText);
    }

    public static void showCustomInputDialog(Context context, int layoutId,
                                             int submitBtnId, int inputEtId,
                                             int closeBtnId,
                                             OnInputConfirmListener listener) {

        View dialogView = LayoutInflater.from(context).inflate(layoutId, null);
        if (dialogView == null) return;
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                context,
                com.bif.app.core.R.style.ThemeOverlay_BIFLocator_MaterialAlertDialog
        )
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (closeBtnId > 0) {
            View btnClose = dialogView.findViewById(closeBtnId);
            if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        View btnSubmit = dialogView.findViewById(submitBtnId);
        View etInput = dialogView.findViewById(inputEtId);
        if (btnSubmit instanceof Button && etInput instanceof EditText) {
            btnSubmit.setOnClickListener(v -> {
                String text = ((EditText) etInput).getText().toString().trim();
                if (listener != null) listener.onConfirm(text);
                dialog.dismiss();
            });
        }

        dialog.show();
    }
}
