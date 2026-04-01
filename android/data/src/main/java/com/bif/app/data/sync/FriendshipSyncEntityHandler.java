package com.bif.app.data.sync;

import android.util.Log;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.data.mapper.FriendshipMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;
import com.google.gson.Gson;

public class FriendshipSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "FriendshipSyncHandler";

    private final FriendshipDao friendshipDao;
    private final FriendDao friendDao;
    private final Gson gson;

    public FriendshipSyncEntityHandler(FriendshipDao friendshipDao,
                                       FriendDao friendDao,
                                       Gson gson) {
        this.friendshipDao = friendshipDao;
        this.friendDao = friendDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "friendship";
    }

    @Override
    public String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof String) {
            return (String) payload;
        }
        return gson.toJson(payload);
    }

    @Override
    public void applyPulledChange(SyncChangeDto change, String activeUserId) {
        if (change == null) {
            return;
        }

        if ("DELETE".equalsIgnoreCase(change.operation)) {
            if (change.entityId != null && !change.entityId.isEmpty()) {
                friendshipDao.deleteByServerId(change.entityId);
            }
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            FriendshipApiModel payload = gson.fromJson(change.payload,
                    FriendshipApiModel.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            FriendshipEntity entity = FriendshipMapper.fromApi(payload);
            friendshipDao.insert(entity);

            if (entity.status == FriendshipStatus.ACCEPTED) {
                upsertFriendFromFriendship(entity, activeUserId);
            }
            if (entity.status == FriendshipStatus.REJECTED
                    || entity.status == FriendshipStatus.CANCELED) {
                removeFriendFromFriendship(entity, activeUserId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled friendship change", e);
        }
    }

    private void upsertFriendFromFriendship(FriendshipEntity friendship,
                                            String activeUserId) {
        String counterpartId = resolveCounterpartId(friendship, activeUserId);
        if (counterpartId == null || counterpartId.isEmpty()) {
            return;
        }

        FriendEntity existing = friendDao.getByServerUserId(counterpartId);
        FriendEntity entity = existing != null ? existing : new FriendEntity();
        entity.id = stableId(counterpartId);
        entity.serverUserId = counterpartId;
        entity.name = counterpartId;
        entity.avatarLetter = safeAvatarLetter(counterpartId);
        entity.avatarColor = 0xFF03DAC5;
        entity.isOnline = false;
        friendDao.insert(entity);
    }

    private void removeFriendFromFriendship(FriendshipEntity friendship,
                                            String activeUserId) {
        String counterpartId = resolveCounterpartId(friendship, activeUserId);
        if (counterpartId == null || counterpartId.isEmpty()) {
            return;
        }
        friendDao.deleteByServerUserId(counterpartId);
    }

    private String resolveCounterpartId(FriendshipEntity friendship,
                                        String activeUserId) {
        if (activeUserId != null && activeUserId.equals(friendship.requesterId)) {
            return friendship.receiverId;
        }
        if (activeUserId != null && activeUserId.equals(friendship.receiverId)) {
            return friendship.requesterId;
        }
        return friendship.requesterId;
    }

    private int stableId(String source) {
        return Math.abs(source.hashCode());
    }

    private String safeAvatarLetter(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "?";
        }
        return source.trim().substring(0, 1).toUpperCase();
    }
}

