package com.bif.app.core.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

public class DialogUtils {
    private static final String TAG = "DialogUtils";
    public static void showConfirmDialog(Context context, String title, String message,
                                         String positiveText, String negativeText,
                                         Runnable onConfirm) {
        new AlertDialog.Builder(context)
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

    public interface DialogViewReadyListener {
        void onReady(View dialogView, androidx.appcompat.app.AlertDialog dialog);
    }

    public static void showCustomViewDialog(Context context, int layoutId,
                                            int closeBtnId,
                                            DialogViewReadyListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(layoutId, null);
        if (dialogView == null) return;

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(context)
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
        AlertDialog dialog = new AlertDialog.Builder(context)
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
