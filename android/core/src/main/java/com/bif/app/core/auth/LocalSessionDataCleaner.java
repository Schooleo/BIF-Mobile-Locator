package com.bif.app.core.auth;

import javax.inject.Inject;

@FunctionalInterface
public interface LocalSessionDataCleaner {
    @Inject
    void clearLocalUserData();
}
