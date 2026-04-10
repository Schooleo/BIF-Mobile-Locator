package com.bif.server.features.sync.services;

public class SyncPushApplyResult {
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_ALREADY_APPLIED = "ALREADY_APPLIED";
    public static final String STATUS_REJECTED_VALIDATION = "REJECTED_VALIDATION";
    public static final String STATUS_RETRYABLE_FAILURE = "RETRYABLE_FAILURE";

    private final String payload;
    private final String status;
    private final String reasonCode;
    private final Long serverVersion;
    private final boolean persistChangeLog;

    private SyncPushApplyResult(String payload,
                                String status,
                                String reasonCode,
                                Long serverVersion,
                                boolean persistChangeLog) {
        this.payload = payload;
        this.status = status;
        this.reasonCode = reasonCode;
        this.serverVersion = serverVersion;
        this.persistChangeLog = persistChangeLog;
    }

    public static SyncPushApplyResult applied(String payload, long serverVersion) {
        return new SyncPushApplyResult(payload, STATUS_APPLIED, "APPLIED",
                serverVersion, true);
    }

    public static SyncPushApplyResult rejectedValidation(String reasonCode) {
        return new SyncPushApplyResult(null, STATUS_REJECTED_VALIDATION,
                reasonCode, null, false);
    }

    public static SyncPushApplyResult retryableFailure(String reasonCode) {
        return new SyncPushApplyResult(null, STATUS_RETRYABLE_FAILURE,
                reasonCode, null, false);
    }

    public String getPayload() {
        return payload;
    }

    public String getStatus() {
        return status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public Long getServerVersion() {
        return serverVersion;
    }

    public boolean shouldPersistChangeLog() {
        return persistChangeLog;
    }
}
