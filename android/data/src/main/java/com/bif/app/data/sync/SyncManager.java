package com.bif.app.data.sync;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.core.network.dto.SyncRequestDto;
import com.bif.app.core.network.dto.SyncResponseDto;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.PlaceDao;
import com.bif.app.data.source.local.SyncQueueDao;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class SyncManager {

    private static final String TAG = "SyncManager";
    private static final int MAX_RETRY_COUNT = 5;
    private static final String PREF_NAME = "SYNC_PREF";
    private static final String KEY_USER_ID = "sync_user_id";
    private static final String KEY_DEVICE_ID = "sync_device_id";
    private static final String KEY_LAST_PULLED_VERSION = "last_pulled_version";

    private final RestApiService restApiService;
    private final SyncQueueDao syncQueueDao;
    private final NetworkMonitor networkMonitor;
    private final SharedPreferences syncPrefs;
    private final Gson gson;
    private final Map<String, SyncEntityHandler> handlersByEntityType;
    private final ExecutorService reconnectSyncExecutor;

    private String userId;
    private String deviceId;
    private long lastPulledVersion;
    private boolean lastObservedOnline;

    @Inject
    public SyncManager(RestApiService restApiService,
                       SyncQueueDao syncQueueDao,
                       PlaceDao placeDao,
                       FavoriteDao favoriteDao,
                       NetworkMonitor networkMonitor,
                       @ApplicationContext Context appContext) {
        this.restApiService = restApiService;
        this.syncQueueDao = syncQueueDao;
        this.networkMonitor = networkMonitor;
        this.syncPrefs = appContext.getSharedPreferences(PREF_NAME,
                Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.handlersByEntityType = new HashMap<>();
        this.reconnectSyncExecutor = Executors.newSingleThreadExecutor();
        this.lastObservedOnline = networkMonitor.isOnline();
        registerHandler(new PlaceSyncEntityHandler(placeDao, gson));
        registerHandler(new FavoriteSyncEntityHandler(favoriteDao, gson));
        loadPersistedSyncState();
        registerReconnectAutoSync();
    }

    // Backward-compatible constructor used by unit tests.
    SyncManager(RestApiService restApiService,
                SyncQueueDao syncQueueDao,
                NetworkMonitor networkMonitor) {
        this.restApiService = restApiService;
        this.syncQueueDao = syncQueueDao;
        this.networkMonitor = networkMonitor;
        this.syncPrefs = null;
        this.gson = new Gson();
        this.handlersByEntityType = new HashMap<>();
        this.reconnectSyncExecutor = Executors.newSingleThreadExecutor();
        this.lastObservedOnline = networkMonitor != null
            && networkMonitor.isOnline();
        registerReconnectAutoSync();
    }

    public void setUserContext(String userId, String deviceId) {
        this.userId = userId != null ? userId.trim() : null;
        this.deviceId = resolveDeviceId(deviceId);

        if (syncPrefs != null) {
            syncPrefs.edit()
                    .putString(KEY_USER_ID, this.userId)
                    .putString(KEY_DEVICE_ID, this.deviceId)
                    .apply();
        }
    }

    public void setLastPulledVersion(long version) {
        this.lastPulledVersion = version;
        if (syncPrefs != null) {
            syncPrefs.edit()
                    .putLong(KEY_LAST_PULLED_VERSION, version)
                    .apply();
        }
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
        ensureSyncContext();
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "Missing user context - skipping sync");
            return null;
        }

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
            change.serverVersion = lastPulledVersion;
            change.operation = entry.operation;
            change.clientChangeId = entry.clientChangeId;
            change.payload = entry.payload;
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

                applyPulledChanges(syncResponse.pulledChanges);

                // Update last pulled version
                setLastPulledVersion(syncResponse.currentServerVersion);

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
        enqueueChange(entityType, entityId, operation,
                clientChangeId, null);
    }

    /**
     * Enqueue a change with optional payload for later sync.
     */
    public void enqueueChange(String entityType, String entityId,
                              String operation, String clientChangeId,
                              Object payload) {
        SyncQueueEntity entry = new SyncQueueEntity();
        entry.entityType = entityType;
        entry.entityId = entityId;
        entry.operation = operation;
        entry.clientChangeId = clientChangeId;
        entry.payload = serializePayload(entityType, payload);
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

    /**
     * Expose current connectivity status for repository-layer decisions.
     */
    public boolean isOnline() {
        return networkMonitor.isOnline();
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

    private void applyPulledChanges(List<SyncChangeDto> pulledChanges) {
        if (pulledChanges == null || pulledChanges.isEmpty()) {
            return;
        }

        for (SyncChangeDto change : pulledChanges) {
            if (change.entityType == null) {
                continue;
            }

            SyncEntityHandler handler = handlersByEntityType.get(
                    change.entityType.toLowerCase());
            if (handler == null) {
                continue;
            }
            handler.applyPulledChange(change, userId);
        }
    }

    private void ensureSyncContext() {
        if (userId == null || userId.isEmpty()) {
            loadPersistedSyncState();
        }
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = resolveDeviceId(null);
            if (syncPrefs != null) {
                syncPrefs.edit()
                        .putString(KEY_DEVICE_ID, deviceId)
                        .apply();
            }
        }
    }

    private void loadPersistedSyncState() {
        if (syncPrefs == null) {
            return;
        }

        userId = syncPrefs.getString(KEY_USER_ID, userId);
        deviceId = syncPrefs.getString(KEY_DEVICE_ID, deviceId);
        lastPulledVersion = syncPrefs.getLong(KEY_LAST_PULLED_VERSION,
                lastPulledVersion);
    }

    private String resolveDeviceId(String preferred) {
        if (preferred != null && !preferred.trim().isEmpty()) {
            return preferred.trim();
        }

        if (deviceId != null && !deviceId.trim().isEmpty()) {
            return deviceId;
        }

        if (syncPrefs != null) {
            String persistedDeviceId = syncPrefs.getString(KEY_DEVICE_ID,
                    null);
            if (persistedDeviceId != null
                    && !persistedDeviceId.trim().isEmpty()) {
                return persistedDeviceId;
            }
        }

        String generatedId = "app-" + UUID.randomUUID();
        if (syncPrefs != null) {
            syncPrefs.edit()
                    .putString(KEY_DEVICE_ID, generatedId)
                    .apply();
        }
        return generatedId;
    }

    private void registerHandler(SyncEntityHandler handler) {
        handlersByEntityType.put(handler.entityType().toLowerCase(),
                handler);
    }

    private void registerReconnectAutoSync() {
        if (networkMonitor == null) {
            return;
        }

        LiveData<Boolean> connectivity = networkMonitor.observeConnectivity();
        if (connectivity == null) {
            return;
        }

        connectivity.observeForever(connected -> {
            boolean online = Boolean.TRUE.equals(connected);
            if (online && !lastObservedOnline) {
                reconnectSyncExecutor.execute(this::syncIfOnline);
            }
            lastObservedOnline = online;
        });
    }

    private String serializePayload(String entityType, Object payload) {
        if (payload == null) {
            return null;
        }

        if (entityType != null) {
            SyncEntityHandler handler = handlersByEntityType.get(
                    entityType.toLowerCase());
            if (handler != null) {
                return handler.serializePayload(payload);
            }
        }

        if (payload instanceof String) {
            return (String) payload;
        }
        return gson.toJson(payload);
    }
}
