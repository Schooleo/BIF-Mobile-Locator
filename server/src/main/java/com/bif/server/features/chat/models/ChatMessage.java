package com.bif.server.features.chat.models;

import com.bif.server.common.models.Location;
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

    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_LOCATION = "LOCATION";
    public static final String TYPE_EVENT = "EVENT";
    public static final String TYPE_TRIP_CREATED_CARD = "TRIP_CREATED_CARD";
    public static final String TYPE_AI_SUGGESTED_PLACES_CARD = "AI_SUGGESTED_PLACES_CARD";
    public static final String TYPE_PLACE_SHARE_CARD = "PLACE_SHARE_CARD";

    private String type = TYPE_TEXT;
    private Location sharedLocation;
    private String sharedAddress;
    private boolean confirmed;
}