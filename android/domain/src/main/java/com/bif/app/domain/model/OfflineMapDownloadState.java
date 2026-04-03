package com.bif.app.domain.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class OfflineMapDownloadState {

    public enum Status {
        IDLE,
        DOWNLOADING,
        COMPLETED,
        ALREADY_DOWNLOADED,
        FAILED
    }

    public final Status status;
    public final int progressPercent;
    public final boolean indeterminate;
    @Nullable
    public final String errorMessage;

    private OfflineMapDownloadState(
            @NonNull Status status,
            int progressPercent,
            boolean indeterminate,
            @Nullable String errorMessage) {
        this.status = status;
        this.progressPercent = progressPercent;
        this.indeterminate = indeterminate;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public static OfflineMapDownloadState idle() {
        return new OfflineMapDownloadState(Status.IDLE, 0, false, null);
    }

    @NonNull
    public static OfflineMapDownloadState downloading(int progressPercent, boolean indeterminate) {
        return new OfflineMapDownloadState(
                Status.DOWNLOADING,
                Math.max(0, Math.min(100, progressPercent)),
                indeterminate,
                null);
    }

    @NonNull
    public static OfflineMapDownloadState completed() {
        return new OfflineMapDownloadState(Status.COMPLETED, 100, false, null);
    }

    @NonNull
    public static OfflineMapDownloadState alreadyDownloaded() {
        return new OfflineMapDownloadState(Status.ALREADY_DOWNLOADED, 100, false, null);
    }

    @NonNull
    public static OfflineMapDownloadState failed(@Nullable String errorMessage) {
        return new OfflineMapDownloadState(Status.FAILED, 0, false, errorMessage);
    }
}
