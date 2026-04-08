package com.bif.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatMessage {
    public enum MessageType {
        TEXT,
        LOCATION,
        SYSTEM,
        TRIP_CREATED_CARD,
        AI_SUGGESTED_PLACES_CARD,
        UNKNOWN
    }

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
    private final TripCreatedCardData tripCreatedCardData;
    private final SuggestedPlacesCardData suggestedPlacesCardData;

    public ChatMessage(String id, String groupId, String senderUserId,
                       String senderName, String content, String type,
                       long sentAt, String clientMessageId,
                       double sharedLatitude, double sharedLongitude,
                       String sharedAddress, boolean confirmed,
                       boolean isOutgoing) {
        this(id, groupId, senderUserId, senderName, content, type, sentAt, clientMessageId,
                sharedLatitude, sharedLongitude, sharedAddress, confirmed, isOutgoing,
                null, null);
    }

    public ChatMessage(String id, String groupId, String senderUserId,
                       String senderName, String content, String type,
                       long sentAt, String clientMessageId,
                       double sharedLatitude, double sharedLongitude,
                       String sharedAddress, boolean confirmed,
                       boolean isOutgoing,
                       TripCreatedCardData tripCreatedCardData,
                       SuggestedPlacesCardData suggestedPlacesCardData) {
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
        this.tripCreatedCardData = tripCreatedCardData;
        this.suggestedPlacesCardData = suggestedPlacesCardData;
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
    public TripCreatedCardData getTripCreatedCardData() { return tripCreatedCardData; }
    public SuggestedPlacesCardData getSuggestedPlacesCardData() { return suggestedPlacesCardData; }

    public boolean isTextMessage() { return "TEXT".equals(type); }
    public boolean isLocationMessage() { return "LOCATION".equals(type); }
    public boolean isSystemMessage() { return "SYSTEM".equals(type); }

    public MessageType getMessageType() {
        if (type == null) return MessageType.TEXT;
        switch (type) {
            case "TEXT":
                return MessageType.TEXT;
            case "LOCATION":
                return MessageType.LOCATION;
            case "SYSTEM":
                return MessageType.SYSTEM;
            case "TRIP_CREATED_CARD":
                return MessageType.TRIP_CREATED_CARD;
            case "AI_SUGGESTED_PLACES_CARD":
                return MessageType.AI_SUGGESTED_PLACES_CARD;
            default:
                return MessageType.UNKNOWN;
        }
    }

    public static class TripCreatedCardData {
        private final String tripId;
        private final int stopCount;
        private final long startTime;
        private final double totalDistance;
        private final boolean isSaved;

        public TripCreatedCardData(String tripId,
                                   int stopCount,
                                   long startTime,
                                   double totalDistance,
                                   boolean isSaved) {
            this.tripId = tripId;
            this.stopCount = stopCount;
            this.startTime = startTime;
            this.totalDistance = totalDistance;
            this.isSaved = isSaved;
        }

        public String getTripId() { return tripId; }
        public int getStopCount() { return stopCount; }
        public long getStartTime() { return startTime; }
        public double getTotalDistance() { return totalDistance; }
        public boolean isSaved() { return isSaved; }
    }

    public static class SuggestedPlacesCardData {
        private final String tripId;
        private final List<Place> places;

        public SuggestedPlacesCardData(String tripId, List<Place> places) {
            this.tripId = tripId;
            this.places = places == null
                    ? Collections.emptyList()
                    : new ArrayList<>(places);
        }

        public String getTripId() { return tripId; }
        public List<Place> getPlaces() { return new ArrayList<>(places); }
    }
}
