package com.bif.app.data.sync.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.favorite.FavoriteDto;
import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.network.dto.sync.SyncPushResultDto;
import com.bif.app.core.network.dto.sync.SyncRequestDto;
import com.bif.app.core.network.dto.sync.SyncResponseDto;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.dao.ChatMessageDao;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.dao.FriendshipDao;
import com.bif.app.data.source.local.dao.GroupDao;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.handler.ChatMessageSyncEntityHandler;
import com.bif.app.data.sync.handler.FavoriteSyncEntityHandler;
import com.bif.app.data.sync.handler.GroupSyncEntityHandler;
import com.bif.app.data.sync.handler.PlaceSyncEntityHandler;
import com.bif.app.data.sync.handler.ProfileSyncEntityHandler;
import com.bif.app.data.sync.handler.ReviewSyncEntityHandler;
import com.bif.app.data.sync.handler.SyncEntityHandler;
import com.bif.app.data.sync.handler.TripStopSyncEntityHandler;
import com.bif.app.data.sync.handler.TripSyncEntityHandler;
import com.bif.app.data.source.AndroidGeocodingDataSource;
import com.bif.app.domain.sync.ISyncInitializable;
import com.google.gson.Gson;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class SyncManager implements ISyncInitializable {

    private static final String TAG = "SyncManager";
    private static final int MAX_RETRY_COUNT = 5;
    public static final String QUEUE_STATUS_PENDING = "PENDING";
    public static final String QUEUE_STATUS_IN_FLIGHT = "IN_FLIGHT";
    public static final String QUEUE_STATUS_FAILED = "FAILED";
    public static final String QUEUE_STATUS_BLOCKED = "BLOCKED";
    public static final String PUSH_STATUS_APPLIED = "APPLIED";
    public static final String PUSH_STATUS_ALREADY_APPLIED = "ALREADY_APPLIED";
    public static final String PUSH_STATUS_REJECTED_VALIDATION = "REJECTED_VALIDATION";
    public static final String PUSH_STATUS_RETRYABLE_FAILURE = "RETRYABLE_FAILURE";
    private static final String PREF_NAME = "SYNC_PREF";
    private static final String KEY_USER_ID = "sync_user_id";
    private static final String KEY_DEVICE_ID = "sync_device_id";
    private static final String KEY_LAST_PULLED_VERSION = "last_pulled_version";
    private static final int MAX_CACHED_MESSAGES_PER_GROUP = 30;

    private final RestApiService restApiService;
    private final SyncQueueDao syncQueueDao;
    private final FavoriteDao favoriteDao;
    private final PlaceDao placeDao;
    private final ChatMessageDao chatMessageDao;
    private final GroupDao groupDao;
    private final NetworkMonitor networkMonitor;
    private final AndroidGeocodingDataSource geocodingDataSource;
    private final SharedPreferences syncPrefs;
    private final Gson gson;
    private final Map<String, SyncEntityHandler> handlersByEntityType;
    private final Context appContext;
    private final ExecutorService enqueueExecutor;
    private final ExecutorService reconnectSyncExecutor;
    private final AppDatabase appDatabase;

    private String userId;
    private String deviceId;
    private long lastPulledVersion;
    private boolean lastObservedOnline;

    /**
     * Redacts a userId by returning either a masked version or a static placeholder.
     * Protects PII from appearing in logs.
     */
    private String redactUserId(String id) {
        if (id == null || id.isEmpty()) {
            return "<null>";
        }
        if (id.length() <= 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }

    @Inject
    public SyncManager(RestApiService restApiService,
            SyncQueueDao syncQueueDao,
            PlaceDao placeDao,
            TripDao tripDao,
            ChatMessageDao chatMessageDao,
            GroupDao groupDao,
            FriendDao friendDao,
            FriendshipDao friendshipDao,
            FavoriteDao favoriteDao,
            ProfileDao profileDao,
            ReviewDao reviewDao,
            AppDatabase appDatabase,
            NetworkMonitor networkMonitor,
            AndroidGeocodingDataSource geocodingDataSource,
            @ApplicationContext Context appContext) {
        this.restApiService = restApiService;
        this.syncQueueDao = syncQueueDao;
        this.favoriteDao = favoriteDao;
        this.placeDao = placeDao;
        this.chatMessageDao = chatMessageDao;
        this.groupDao = groupDao;
        this.networkMonitor = networkMonitor;
        this.geocodingDataSource = geocodingDataSource;
        this.syncPrefs = appContext != null
            ? appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            : null;
        this.gson = new Gson();
        this.appContext = appContext;
        this.appDatabase = appDatabase;
        this.enqueueExecutor = Executors.newSingleThreadExecutor();
        this.handlersByEntityType = new HashMap<>();
        this.reconnectSyncExecutor = Executors.newSingleThreadExecutor();
        this.lastObservedOnline = networkMonitor.isOnline();

        if (placeDao != null) {
            registerHandler(new PlaceSyncEntityHandler(placeDao, gson));
        }
        if (tripDao != null) {
            registerHandler(new TripSyncEntityHandler(tripDao, gson));
            registerHandler(new TripStopSyncEntityHandler(tripDao, gson));
        }
        if (chatMessageDao != null) {
            registerHandler(new ChatMessageSyncEntityHandler(chatMessageDao, gson));
        }
        if (groupDao != null) {
            registerHandler(new GroupSyncEntityHandler(groupDao, gson));
        }
        if (favoriteDao != null) {
            registerHandler(new FavoriteSyncEntityHandler(favoriteDao, gson));
        }
        if (profileDao != null && appContext != null) {
            registerHandler(new ProfileSyncEntityHandler(profileDao, gson, appContext));
        }
        if (reviewDao != null && placeDao != null && syncQueueDao != null && appDatabase != null) {
            registerHandler(new ReviewSyncEntityHandler(
                    reviewDao,
                    placeDao,
                    syncQueueDao,
                    appDatabase,
                    gson));
        }

        loadPersistedSyncState();
        registerReconnectAutoSync();
    }

    SyncManager(RestApiService restApiService,
            SyncQueueDao syncQueueDao,
            NetworkMonitor networkMonitor) {
        this(restApiService, syncQueueDao, null, null, null, null, null,
            null, null, null, null, null, networkMonitor, null, null);
    }

    SyncManager(RestApiService restApiService,
                SyncQueueDao syncQueueDao,
                FavoriteDao favoriteDao,
                PlaceDao placeDao,
                AndroidGeocodingDataSource geocodingDataSource,
                NetworkMonitor networkMonitor) {
        this(restApiService, syncQueueDao, placeDao, null, null, null,
            null, null, favoriteDao, null, null, null,
            networkMonitor, geocodingDataSource, null);
    }

    @Override
    public void setUserContext(String userId, String deviceId) {
        String normalizedUserId = userId != null ? userId.trim() : null;
        boolean hasCurrentUser = this.userId != null
                && !this.userId.isEmpty();
        boolean hasIncomingUser = normalizedUserId != null
                && !normalizedUserId.isEmpty();
        if (hasCurrentUser && hasIncomingUser
                && !this.userId.equals(normalizedUserId)) {
            Log.w(TAG, "Sync user context changed. oldUserId=" + redactUserId(this.userId)
                + " newUserId=" + redactUserId(normalizedUserId)
                + " -> reset lastPulledVersion");
            setLastPulledVersion(0L);
        }

        this.userId = normalizedUserId;
        this.deviceId = resolveDeviceId(deviceId);

        if (syncPrefs != null) {
            syncPrefs.edit()
                    .putString(KEY_USER_ID, this.userId)
                    .putString(KEY_DEVICE_ID, this.deviceId)
                    .apply();
        }
    }

    @Override
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

    @Override
    public void resetSyncContext() {
        userId = null;
        lastPulledVersion = 0L;

        if (syncPrefs == null) {
            return;
        }

        boolean committed = syncPrefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_LAST_PULLED_VERSION)
                .commit();
        if (!committed) {
            Log.w(TAG, "Failed to commit sync context reset");
        }
    }

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

        waitForPendingEnqueues();
        syncQueueDao.resetInFlight();

        SyncRequestDto request = new SyncRequestDto();
        request.userId = userId;
        request.deviceId = deviceId;
        request.lastPulledVersion = lastPulledVersion;

        List<SyncQueueEntity> pending = syncQueueDao.getPendingForUser(userId);
        promotePreviewFavoritesBeforeSync(pending, userId);
        List<SyncChangeDto> pushedChanges = new ArrayList<>();
        List<SyncQueueEntity> pushedEntries = new ArrayList<>();
        for (SyncQueueEntity entry : pending) {
            if (!isOwnedByActiveUser(entry, userId)) {
                entry.status = QUEUE_STATUS_BLOCKED;
                syncQueueDao.update(entry);
                Log.w(TAG, "Blocked sync entry due to user ownership mismatch. entryId="
                        + entry.id + " activeUserId=" + redactUserId(userId));
                continue;
            }

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
            pushedEntries.add(entry);
        }
        request.pushedChanges = pushedChanges.isEmpty()
                ? null
                : pushedChanges;

        try {
            Response<SyncResponseDto> response = restApiService
                    .sync(request).execute();

            if (response.isSuccessful() && response.body() != null) {
                SyncResponseDto syncResponse = response.body();
                settlePendingEntries(pushedEntries, syncResponse);

                applyPulledChanges(syncResponse.pulledChanges);
                hydrateChatCachesForAllGroups();

                Log.d(TAG, "sync: updating lastPulledVersion to " + syncResponse.currentServerVersion);
                setLastPulledVersion(syncResponse.currentServerVersion);

                if (syncResponse.conflicts != null) {
                    Log.w(TAG, "Sync conflicts detected: "
                            + syncResponse.conflicts.size());
                }

                return syncResponse;
            } else {
                Log.e(TAG, "Sync failed with status=" + response.code()
                        + " message=" + response.message());
                handleFailedPush(pushedEntries);
                return null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Sync network error", e);
            handleFailedPush(pushedEntries);
            return null;
        }
    }

    public void enqueueChange(String entityType, String entityId,
            String operation, String clientChangeId) {
        enqueueChange(entityType, entityId, operation,
                clientChangeId, null);
    }

    public void enqueueChange(String entityType, String entityId,
            String operation, String clientChangeId,
            Object payload) {
        enqueueExecutor.execute(() -> {
            if (this.userId == null || this.userId.isEmpty()) {
                Log.w(TAG, "Cannot enqueue change: user context is null or empty");
                return;
            }
            
            SyncQueueEntity entry = new SyncQueueEntity();
            entry.userId = userId;
            entry.entityType = entityType;
            entry.entityId = entityId;
            entry.operation = operation;
            entry.clientChangeId = clientChangeId;
            entry.payload = serializePayload(entityType, payload);
            entry.status = QUEUE_STATUS_PENDING;
            entry.retryCount = 0;
            entry.createdAt = System.currentTimeMillis();
            syncQueueDao.enqueue(entry);
        });
    }

    @Override
    public void syncIfOnline() {
        reconnectSyncExecutor.execute(() -> {
            if (networkMonitor.isOnline()) {
                sync();
            }
        });
    }

    public boolean isOnline() {
        return networkMonitor.isOnline();
    }

    public boolean areTrackedChangesAccepted(SyncResponseDto syncResponse,
                                             List<String> trackedClientChangeIds) {
        if (syncResponse == null) {
            return false;
        }
        if (trackedClientChangeIds == null || trackedClientChangeIds.isEmpty()) {
            return true;
        }
        for (String clientChangeId : trackedClientChangeIds) {
            if (!isAcceptedPushResult(syncResponse, clientChangeId)) {
                return false;
            }
        }
        return true;
    }

    private void handleFailedPush(List<SyncQueueEntity> failed) {
        for (SyncQueueEntity entry : failed) {
            entry.retryCount++;
            if (entry.retryCount > MAX_RETRY_COUNT) {
                entry.status = QUEUE_STATUS_FAILED;
                Log.e(TAG, "Change exceeded max retries: "
                        + entry.clientChangeId);
            } else {
                entry.status = QUEUE_STATUS_PENDING;
            }
            syncQueueDao.update(entry);
        }
    }

    private void settlePendingEntries(List<SyncQueueEntity> pending,
                                      SyncResponseDto syncResponse) {
        Map<String, SyncPushResultDto> resultsByClientChangeId =
                indexPushResults(syncResponse != null ? syncResponse.pushResults : null);
        Set<Integer> removedIds = new HashSet<>();

        for (SyncQueueEntity entry : pending) {
            SyncPushResultDto result = resultsByClientChangeId.get(entry.clientChangeId);
            if (result == null || result.status == null || result.status.trim().isEmpty()) {
                entry.status = QUEUE_STATUS_PENDING;
                syncQueueDao.update(entry);
                Log.w(TAG, "Missing push result for clientChangeId=" + entry.clientChangeId);
                continue;
            }

            String normalizedStatus = result.status.trim().toUpperCase(Locale.ROOT);
            switch (normalizedStatus) {
                case PUSH_STATUS_APPLIED:
                case PUSH_STATUS_ALREADY_APPLIED:
                    clearPendingSyncIfFavorite(entry);
                    syncQueueDao.remove(entry.id);
                    removedIds.add(entry.id);
                    break;
                case PUSH_STATUS_REJECTED_VALIDATION:
                    entry.status = QUEUE_STATUS_BLOCKED;
                    syncQueueDao.update(entry);
                    Log.w(TAG, "Blocked sync change clientChangeId=" + entry.clientChangeId
                            + " reason=" + result.reasonCode);
                    break;
                case PUSH_STATUS_RETRYABLE_FAILURE:
                    entry.retryCount++;
                    if (entry.retryCount > MAX_RETRY_COUNT) {
                        entry.status = QUEUE_STATUS_FAILED;
                    } else {
                        entry.status = QUEUE_STATUS_PENDING;
                    }
                    syncQueueDao.update(entry);
                    Log.w(TAG, "Retryable sync failure clientChangeId=" + entry.clientChangeId
                            + " reason=" + result.reasonCode);
                    break;
                default:
                    entry.status = QUEUE_STATUS_PENDING;
                    syncQueueDao.update(entry);
                    Log.w(TAG, "Unknown push result status clientChangeId=" + entry.clientChangeId
                            + " status=" + result.status);
                    break;
            }
        }

        for (SyncQueueEntity entry : pending) {
            if (!removedIds.contains(entry.id) && QUEUE_STATUS_IN_FLIGHT.equals(entry.status)) {
                entry.status = QUEUE_STATUS_PENDING;
                syncQueueDao.update(entry);
            }
        }
    }

    private Map<String, SyncPushResultDto> indexPushResults(List<SyncPushResultDto> pushResults) {
        Map<String, SyncPushResultDto> resultsByClientChangeId = new HashMap<>();
        if (pushResults == null) {
            return resultsByClientChangeId;
        }
        for (SyncPushResultDto result : pushResults) {
            if (result == null || result.clientChangeId == null
                    || result.clientChangeId.trim().isEmpty()) {
                continue;
            }
            resultsByClientChangeId.put(result.clientChangeId, result);
        }
        return resultsByClientChangeId;
    }

    private boolean isAcceptedPushResult(SyncResponseDto syncResponse,
                                         String clientChangeId) {
        if (syncResponse == null || syncResponse.pushResults == null
                || clientChangeId == null || clientChangeId.trim().isEmpty()) {
            return false;
        }
        for (SyncPushResultDto result : syncResponse.pushResults) {
            if (result == null || result.clientChangeId == null) {
                continue;
            }
            if (!clientChangeId.equals(result.clientChangeId)) {
                continue;
            }
            return PUSH_STATUS_APPLIED.equals(result.status)
                    || PUSH_STATUS_ALREADY_APPLIED.equals(result.status);
        }
        return false;
    }

    private void applyPulledChanges(List<SyncChangeDto> pulledChanges) {

        if (pulledChanges == null || pulledChanges.isEmpty()) {
            return;
        }


        if (appDatabase != null) {

            appDatabase.runInTransaction(() -> {
                for (SyncChangeDto change : pulledChanges) {
                    applySingleChange(change);
                }
            });

        } else {
            for (SyncChangeDto change : pulledChanges) {
                applySingleChange(change);
            }
        }
    }

    private void applySingleChange(SyncChangeDto change) {
        if (change.entityType == null) {
            return;
        }

        SyncEntityHandler handler = handlersByEntityType.get(
                change.entityType.toLowerCase(Locale.ROOT));
        if (handler == null) {
            Log.w(TAG, "applyPulledChanges: no handler for entityType=" + change.entityType);
            return;
        }

        handler.applyPulledChange(change, userId);
    }

    private void hydrateChatCachesForAllGroups() {
        if (chatMessageDao == null || groupDao == null || !networkMonitor.isOnline()) {
            return;
        }

        List<GroupEntity> groups;
        try {
            groups = groupDao.getAllGroupsSync();
        } catch (Exception ignored) {
            return;
        }

        if (groups == null || groups.isEmpty()) {
            return;
        }

        for (GroupEntity group : groups) {
            if (group == null || group.isDeleted()) {
                continue;
            }

            String serverId = group.getServerId();
            if (serverId == null || serverId.trim().isEmpty()) {
                continue;
            }

            try {
                Response<List<ChatMessageDto>> response = restApiService.getChatMessages(serverId).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    continue;
                }

                List<ChatMessageEntity> mapped = new ArrayList<>();
                for (ChatMessageDto dto : response.body()) {
                    ChatMessageEntity entity = mapChatDtoToEntity(dto);
                    if (entity != null) {
                        mapped.add(entity);
                    }
                }

                chatMessageDao.deleteByGroupId(serverId);
                if (!mapped.isEmpty()) {
                    chatMessageDao.insertAll(mapped);
                    chatMessageDao.pruneGroupToLimit(serverId,
                            MAX_CACHED_MESSAGES_PER_GROUP);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private ChatMessageEntity mapChatDtoToEntity(ChatMessageDto dto) {
        if (dto == null || dto.groupId == null || dto.groupId.trim().isEmpty()) {
            return null;
        }

        double lat = 0;
        double lng = 0;
        if (dto.sharedLocation != null) {
            lat = dto.sharedLocation.latitude;
            lng = dto.sharedLocation.longitude;
        }

        long sentAt = System.currentTimeMillis();
        if (dto.sentAt != null && !dto.sentAt.trim().isEmpty()) {
            try {
                sentAt = Instant.parse(dto.sentAt).toEpochMilli();
            } catch (Exception ignored) {
            }
        }

        return new ChatMessageEntity(
                dto.id != null ? dto.id : UUID.randomUUID().toString(),
                dto.groupId,
                dto.senderUserId,
                dto.senderName,
                dto.content,
                dto.type,
                sentAt,
                dto.clientMessageId,
                lat,
                lng,
                dto.sharedAddress,
                dto.confirmed);
    }

    private void ensureSyncContext() {
        if (appContext != null) {
            String currentUserId = com.bif.app.core.utils.UserPreferences.getId(appContext);
            if (currentUserId == null || currentUserId.trim().isEmpty()) {
                currentUserId = com.bif.app.core.utils.UserPreferences.getUsername(appContext);
            }
            if (com.bif.app.core.utils.UserPreferences.isLoggedIn(appContext) && currentUserId != null
                    && !currentUserId.trim().isEmpty()) {
                userId = currentUserId;
            } else {
                userId = null;
            }
        }

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
        handlersByEntityType.put(handler.entityType().toLowerCase(Locale.ROOT),
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

    private void waitForPendingEnqueues() {
        CountDownLatch latch = new CountDownLatch(1);
        enqueueExecutor.execute(latch::countDown);
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timed out waiting for pending sync queue writes");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Interrupted while waiting for sync queue writes",
                    interruptedException);
        }
    }

    private String serializePayload(String entityType, Object payload) {
        if (payload == null) {
            return null;
        }

        if (entityType != null) {
            SyncEntityHandler handler = handlersByEntityType.get(
                    entityType.toLowerCase(Locale.ROOT));
            if (handler != null) {
                return handler.serializePayload(payload);
            }
        }

        if (payload instanceof String) {
            return (String) payload;
        }
        return gson.toJson(payload);
    }

    private boolean isOwnedByActiveUser(SyncQueueEntity entry,
                                        String activeUserId) {
        if (entry == null || activeUserId == null || activeUserId.trim().isEmpty()) {
            return false;
        }
        if (entry.userId == null || entry.userId.trim().isEmpty()) {
            return false;
        }
        return activeUserId.equals(entry.userId.trim());
    }

    private void clearPendingSyncIfFavorite(SyncQueueEntity queueEntry) {
        if (favoriteDao == null || queueEntry == null) {
            return;
        }

        if (queueEntry.entityType == null
                || !"favorite".equalsIgnoreCase(queueEntry.entityType)
                || queueEntry.entityId == null
                || queueEntry.entityId.trim().isEmpty()
                || queueEntry.userId == null
                || queueEntry.userId.trim().isEmpty()) {
            return;
        }

        try {
            com.bif.app.data.source.local.entity.FavoriteEntity localFavorite =
                    favoriteDao.findById(queueEntry.entityId.trim(), queueEntry.userId.trim());
            if (localFavorite == null || !localFavorite.pendingSync) {
                return;
            }
            localFavorite.pendingSync = false;
            favoriteDao.update(localFavorite);
        } catch (Exception exception) {
            Log.w(TAG, "Failed to clear pendingSync for favorite entityId="
                    + queueEntry.entityId, exception);
        }
    }

    private void promotePreviewFavoritesBeforeSync(List<SyncQueueEntity> pending,
                                                   String activeUserId) {
        if (pending == null || pending.isEmpty()
                || favoriteDao == null
                || geocodingDataSource == null) {
            return;
        }

        for (SyncQueueEntity entry : pending) {
            if (entry == null
                    || entry.entityType == null
                    || !"favorite".equalsIgnoreCase(entry.entityType)
                    || entry.entityId == null
                    || entry.entityId.trim().isEmpty()) {
                continue;
            }

            try {
                FavoriteEntity localFavorite = favoriteDao.findById(
                        entry.entityId.trim(), activeUserId);
                if (localFavorite == null || !isPreviewFavorite(localFavorite)) {
                    continue;
                }

                FavoriteEntity promoted = promotePreviewFavorite(localFavorite);
                if (promoted == null) {
                    continue;
                }

                favoriteDao.update(promoted);
                FavoriteDto dto = FavoriteMapper.toDto(
                        FavoriteMapper.toDomain(promoted), activeUserId);
                entry.payload = gson.toJson(dto);
                syncQueueDao.update(entry);
            } catch (Exception exception) {
                Log.w(TAG, "Failed to promote preview favorite before sync. entityId="
                        + entry.entityId, exception);
            }
        }
    }

    private FavoriteEntity promotePreviewFavorite(FavoriteEntity favorite) {
        if (favorite == null || favorite.latitude == 0.0d && favorite.longitude == 0.0d) {
            return null;
        }

        List<Address> addresses = geocodingDataSource.reverseGeocodeLocation(
                favorite.latitude,
                favorite.longitude);
        if (addresses == null || addresses.isEmpty() || addresses.get(0) == null) {
            return null;
        }

        Address address = addresses.get(0);
        String resolvedName = trimToNull(address.getFeatureName());
        String resolvedAddress = trimToNull(address.getAddressLine(0));
        if (resolvedName == null && resolvedAddress == null) {
            return null;
        }

        FavoriteEntity promoted = copyFavoriteEntity(favorite);
        if (resolvedName != null) {
            promoted.name = resolvedName;
            promoted.placeName = resolvedName;
        }
        if (resolvedAddress != null) {
            promoted.address = resolvedAddress;
        }
        promoted.externalSource = com.bif.app.domain.model.Place.SOURCE_OSM;
        promoted.externalId = null;
        return promoted;
    }

    private FavoriteEntity copyFavoriteEntity(FavoriteEntity source) {
        if (source == null) {
            return null;
        }

        FavoriteEntity copy = new FavoriteEntity();
        copy.id = source.id;
        copy.placeId = source.placeId;
        copy.externalSource = source.externalSource;
        copy.externalId = source.externalId;
        copy.placeName = source.placeName;
        copy.name = source.name;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        copy.address = source.address;
        copy.description = source.description;
        copy.notes = source.notes;
        copy.rating = source.rating;
        copy.imagePath = source.imagePath;
        copy.userId = source.userId;
        copy.serverVersion = source.serverVersion;
        copy.deleted = source.deleted;
        copy.pendingSync = source.pendingSync;
        return copy;
    }

    private boolean isPreviewFavorite(FavoriteEntity favorite) {
        return favorite != null
                && (isPreviewSource(favorite.externalSource)
                || isBlank(favorite.externalSource));
    }

    private boolean isPreviewSource(String value) {
        return value != null
                && com.bif.app.domain.model.Place.SOURCE_PREVIEW.equalsIgnoreCase(value.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
