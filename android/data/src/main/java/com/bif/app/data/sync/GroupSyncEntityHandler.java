package com.bif.app.data.sync;

import android.util.Log;

import com.bif.app.core.network.dto.GroupApiModel;
import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.google.gson.Gson;

public class GroupSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "GroupSyncHandler";

    private final GroupDao groupDao;
    private final Gson gson;

    public GroupSyncEntityHandler(GroupDao groupDao, Gson gson) {
        this.groupDao = groupDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "group";
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
                groupDao.deleteByServerId(change.entityId);
            }
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) {
            return;
        }

        try {
            GroupApiModel payload = gson.fromJson(change.payload,
                    GroupApiModel.class);
            if (payload == null || payload.id == null || payload.id.isEmpty()) {
                return;
            }

            GroupEntity existing = findByServerIdSync(payload.id);
            int localId = existing != null ? existing.getId()
                    : stableId(payload.id);

            GroupEntity entity = new GroupEntity(
                    localId,
                    payload.id,
                    payload.name,
                    payload.avatarLetter,
                    payload.avatarColor,
                    activeUserId != null && activeUserId.equals(payload.ownerId),
                    payload.ownerId,
                    gson.toJson(payload.memberIds),
                    gson.toJson(payload.memberRoles),
                    Math.max(change.serverVersion, 0L),
                    false,
                    System.currentTimeMillis()
            );
            groupDao.insertGroup(entity);
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled group change", e);
        }
    }

    private GroupEntity findByServerIdSync(String serverId) {
        for (GroupEntity entity : groupDao.getAllGroupsSync()) {
            if (entity != null && serverId.equals(entity.getServerId())) {
                return entity;
            }
        }
        return null;
    }

    private int stableId(String source) {
        return Math.abs(source.hashCode());
    }
}
