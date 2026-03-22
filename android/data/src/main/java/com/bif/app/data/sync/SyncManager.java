package com.bif.app.data.sync;

import android.util.Log;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.core.network.dto.SyncRequestDto;
import com.bif.app.core.network.dto.SyncResponseDto;
import com.bif.app.data.source.local.SyncQueueDao;
import com.bif.app.data.source.local.entity.SyncQueueEntity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

@Singleton
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final int MAX_RETRY_COUNT = 5;

    private final RestApiService restApiService;
    private final SyncQueueDao syncQueueDao;
    private final NetworkMonitor networkMonitor;

    private String userId;
    private String deviceId;
    private long lastPulledVersion;

    @Inject
    public SyncManager(RestApiService restApiService,
                       SyncQueueDao syncQueueDao,
                       NetworkMonitor networkMonitor) {
        this.restApiService = restApiService;
        this.syncQueueDao = syncQueueDao;
        this.networkMonitor = networkMonitor;
    }

    public void setUserContext(String userId, String deviceId) {
        this.userId = userId;
        this.deviceId = deviceId;
    }

    public void setLastPulledVersion(long version) {
        this.lastPulledVersion = version;
    }

    public long getLastPulledVersion() {
        return lastPulledVersion;
    }

    /**
     * Execute a full sync cycle: push pending changes, then pull updates.
     *
     * @return the SyncResponseDto if successful, null if offline or failed
     */
    public SyncResponseDto sync() {
        if (!networkMonitor.isOnline()) {
            Log.d(TAG, "Offline - skipping sync");
            return null;
        }

        // Reset any in-flight entries from a previous crashed sync
        syncQueueDao.resetInFlight();

        // Build the request
        SyncRequestDto request = new SyncRequestDto();
        request.userId = userId;
        request.deviceId = deviceId;
        request.lastPulledVersion = lastPulledVersion;

        // Phase 1: Gather pending changes to push
        List<SyncQueueEntity> pending = syncQueueDao.getPending();
        List<SyncChangeDto> pushedChanges = new ArrayList<>();
        for (SyncQueueEntity entry : pending) {
            entry.status = "IN_FLIGHT";
            syncQueueDao.update(entry);

            SyncChangeDto change = new SyncChangeDto();
            change.entityType = entry.entityType;
            change.entityId = entry.entityId;
            change.operation = entry.operation;
            change.clientChangeId = entry.clientChangeId;
            pushedChanges.add(change);
        }
        request.pushedChanges = pushedChanges.isEmpty()
                ? null : pushedChanges;

        // Phase 2: Execute sync request
        try {
            Response<SyncResponseDto> response = restApiService
                    .sync(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                SyncResponseDto syncResponse = response.body();

                // Mark pushed changes as completed
                for (SyncQueueEntity entry : pending) {
                    syncQueueDao.remove(entry.id);
                }

                // Update last pulled version
                lastPulledVersion = syncResponse.currentServerVersion;

                // Log conflicts if any
                if (syncResponse.conflicts != null) {
                    Log.w(TAG, "Sync conflicts detected: "
                            + syncResponse.conflicts.size());
                }

                return syncResponse;
            } else {
                Log.e(TAG, "Sync failed with status: "
                        + response.code());
                handleFailedPush(pending);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Sync network error", e);
            handleFailedPush(pending);
            return null;
        }
    }

    /**
     * Enqueue a change for later sync.
     */
    public void enqueueChange(String entityType, String entityId,
                               String operation, String clientChangeId) {
        SyncQueueEntity entry = new SyncQueueEntity();
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.operation = operation;
        entry.clientChangeId = clientChangeId;
        entry.status = "PENDING";
        entry.retryCount = 0;
        entry.createdAt = System.currentTimeMillis();
        syncQueueDao.enqueue(entry);
    }

    /**
     * Attempt immediate sync if online, otherwise changes stay queued.
     */
    public void syncIfOnline() {
        if (networkMonitor.isOnline()) {
            sync();
        }
    }

    private void handleFailedPush(List<SyncQueueEntity> failed) {
        for (SyncQueueEntity entry : failed) {
            entry.retryCount++;
            if (entry.retryCount > MAX_RETRY_COUNT) {
                entry.status = "FAILED";
                Log.e(TAG, "Change exceeded max retries: "
                        + entry.clientChangeId);
            } else {
                entry.status = "PENDING";
            }
            syncQueueDao.update(entry);
        }
    }
}
