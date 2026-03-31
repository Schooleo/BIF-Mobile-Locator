package com.bif.app.core.network.dto;

public class ChatMessageDto {
    public String id;
    public String groupId;
    public String senderUserId;
    public String senderName;
    public String content;
    public String type;
    public String sentAt;
    public String clientMessageId;
    public LocationDto sharedLocation;
    public String sharedAddress;
    public boolean confirmed;

    public static class LocationDto {
        public double latitude;
        public double longitude;
    }
}
