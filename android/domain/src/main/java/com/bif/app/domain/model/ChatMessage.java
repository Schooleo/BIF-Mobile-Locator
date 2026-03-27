package com.bif.app.domain.model;

public class ChatMessage {
    private final String id;
    private final String groupId;
    private final String senderUserId;
    private final String senderName;
    private final String content;
    private final String type;
    private final long sentAt;
    private final String clientMessageId;
    private final double sharedLatitude;
    private final double sharedLongitude;
    private final String sharedAddress;
    private boolean confirmed;
    private boolean isOutgoing;

    public ChatMessage(String id, String groupId, String senderUserId,
                       String senderName, String content, String type,
                       long sentAt, String clientMessageId,
                       double sharedLatitude, double sharedLongitude,
                       String sharedAddress, boolean confirmed,
                       boolean isOutgoing) {
        this.id = id;
        this.groupId = groupId;
        this.senderUserId = senderUserId;
        this.senderName = senderName;
        this.content = content;
        this.type = type != null ? type : "TEXT";
        this.sentAt = sentAt;
        this.clientMessageId = clientMessageId;
        this.sharedLatitude = sharedLatitude;
        this.sharedLongitude = sharedLongitude;
        this.sharedAddress = sharedAddress;
        this.confirmed = confirmed;
        this.isOutgoing = isOutgoing;
    }

    public String getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getSenderUserId() { return senderUserId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public String getType() { return type; }
    public long getSentAt() { return sentAt; }
    public String getClientMessageId() { return clientMessageId; }
    public double getSharedLatitude() { return sharedLatitude; }
    public double getSharedLongitude() { return sharedLongitude; }
    public String getSharedAddress() { return sharedAddress; }
    public boolean isConfirmed() { return confirmed; }
    public boolean isOutgoing() { return isOutgoing; }

    public boolean isTextMessage() { return "TEXT".equals(type); }
    public boolean isLocationMessage() { return "LOCATION".equals(type); }
    public boolean isSystemMessage() { return "SYSTEM".equals(type); }
}
