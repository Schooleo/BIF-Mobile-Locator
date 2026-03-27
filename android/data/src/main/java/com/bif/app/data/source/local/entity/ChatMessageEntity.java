package com.bif.app.data.source.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessageEntity {
    @PrimaryKey
    @NonNull
    public String id;

    public String groupId;
    public String senderUserId;
    public String senderName;
    public String content;
    public String type;
    public long sentAt;
    public String clientMessageId;
    public double sharedLatitude;
    public double sharedLongitude;
    public String sharedAddress;
    public boolean confirmed;

    public ChatMessageEntity(@NonNull String id, String groupId,
                             String senderUserId, String senderName,
                             String content, String type, long sentAt,
                             String clientMessageId, double sharedLatitude,
                             double sharedLongitude, String sharedAddress,
                             boolean confirmed) {
        this.id = id;
        this.groupId = groupId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
        this.sentAt = sentAt;
        this.clientMessageId = clientMessageId;
        this.sharedLatitude = sharedLatitude;
        this.sharedLongitude = sharedLongitude;
        this.sharedAddress = sharedAddress;
        this.confirmed = confirmed;
    }
}
