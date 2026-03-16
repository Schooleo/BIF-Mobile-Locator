package com.bif.server.features.chat.models;

import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "chat_messages")
public class ChatMessage extends SyncDocument {
    @Id
    private String id;

    private String groupId;
    private String senderUserId;
    private String content;
    private Instant sentAt;
    private String clientMessageId;
}