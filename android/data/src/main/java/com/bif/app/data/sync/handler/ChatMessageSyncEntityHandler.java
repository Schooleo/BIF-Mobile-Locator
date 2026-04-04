package com.bif.app.data.sync.handler;

import android.util.Log;

import com.bif.app.core.network.dto.chat.ChatMessageDto;
import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.dao.ChatMessageDao;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.google.gson.Gson;

import java.time.Instant;

/**
 * Applies pulled chat message changes from the sync server.
 * Follows the same pattern as {@link PlaceSyncEntityHandler}.
 */
public class ChatMessageSyncEntityHandler implements SyncEntityHandler {

    private static final String TAG = "ChatMsgSyncHandler";
    private static final int MAX_CACHED_MESSAGES_PER_GROUP = 30;

    private final ChatMessageDao chatMessageDao;
    private final Gson gson;

    public ChatMessageSyncEntityHandler(ChatMessageDao chatMessageDao, Gson gson) {
        this.chatMessageDao = chatMessageDao;
        this.gson = gson;
    }

    @Override
    public String entityType() {
        return "chatMessage";
    }

    @Override
    public String serializePayload(Object payload) {
        if (payload == null) return null;
        if (payload instanceof String) return (String) payload;
        return gson.toJson(payload);
    }

    @Override
    public void applyPulledChange(SyncChangeDto change, String activeUserId) {
        if ("DELETE".equalsIgnoreCase(change.operation)) {
            if (change.entityId != null) {
                chatMessageDao.deleteById(change.entityId);
            }
            return;
        }

        if (change.payload == null || change.payload.isEmpty()) return;

        try {
            ChatMessageDto dto = gson.fromJson(change.payload, ChatMessageDto.class);
            if (dto == null || (dto.id == null && change.entityId == null)) return;

            if (dto.id == null) dto.id = change.entityId;

            double lat = 0, lng = 0;
            if (dto.sharedLocation != null) {
                lat = dto.sharedLocation.latitude;
                lng = dto.sharedLocation.longitude;
            }
            long sentAtMillis = 0;
            if (dto.sentAt != null) {
                try {
                    sentAtMillis = Instant.parse(dto.sentAt).toEpochMilli();
                } catch (Exception ignored) {
                    sentAtMillis = System.currentTimeMillis();
                }
            }

            ChatMessageEntity entity = new ChatMessageEntity(
                    dto.id,
                    dto.groupId,
                    dto.senderUserId,
                    null,
                    dto.content,
                    dto.type != null ? dto.type : "TEXT",
                    sentAtMillis,
                    dto.clientMessageId,
                    lat, lng,
                    dto.sharedAddress,
                    dto.confirmed
            );
            chatMessageDao.insert(entity);
            if (dto.groupId != null && !dto.groupId.trim().isEmpty()) {
                chatMessageDao.pruneGroupToLimit(dto.groupId,
                        MAX_CACHED_MESSAGES_PER_GROUP);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed applying pulled chat message change", e);
        }
    }
}


