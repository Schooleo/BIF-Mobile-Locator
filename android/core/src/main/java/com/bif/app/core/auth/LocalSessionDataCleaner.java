package com.bif.app.core.auth;

public interface LocalSessionDataCleaner {
    void clearLocalUserData();
    void clearLocalUserData(Runnable onComplete);
}
