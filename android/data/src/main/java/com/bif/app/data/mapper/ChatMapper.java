package com.bif.app.data.mapper;

import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.domain.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ChatMapper {

    @Inject
    public ChatMapper() {}

    public ChatMessage mapToDomain(ChatMessageEntity entity, String currentUserId) {
        boolean isOutgoing = entity.senderUserId != null
                && entity.senderUserId.equals(currentUserId);
        return new ChatMessage(
                entity.id,
                entity.groupId,
                entity.senderUserId,
                entity.senderName,
                entity.content,
                entity.type,
                entity.sentAt,
                entity.clientMessageId,
                entity.sharedLatitude,
                entity.sharedLongitude,
                entity.sharedAddress,
                entity.confirmed,
                isOutgoing
        );
    }

    public List<ChatMessage> mapToDomainList(List<ChatMessageEntity> entities,
                                              String currentUserId) {
        List<ChatMessage> result = new ArrayList<>();
        if (entities == null) return result;
        for (ChatMessageEntity entity : entities) {
            result.add(mapToDomain(entity, currentUserId));
        }
        return result;
    }

    public ChatMessageEntity mapToEntity(ChatMessage message) {
        return new ChatMessageEntity(
                message.getId(),
                message.getGroupId(),
                message.getSenderUserId(),
                message.getSenderName(),
                message.getContent(),
                message.getType(),
                message.getSentAt(),
                message.getClientMessageId(),
                message.getSharedLatitude(),
                message.getSharedLongitude(),
                message.getSharedAddress(),
                message.isConfirmed()
        );
    }
}
