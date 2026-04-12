package com.bif.app.domain.sync;

public interface ISyncInitializable {
    void resetSyncContext();
    void setLastPulledVersion(long version);
    void setUserContext(String userId, String deviceId);
    void syncIfOnline();
}
