package com.bif.app.core.auth;

@FunctionalInterface
public interface LocalSessionDataCleaner {
    void clearLocalUserData();
}
