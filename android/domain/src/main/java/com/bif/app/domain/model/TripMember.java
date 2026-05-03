package com.bif.app.domain.model;

public class TripMember {

    private final String tripId;
    private final String userId;
    private final String name;
    private final String avatarLetter;
    private final int avatarColor;
    private final String role;

    public TripMember(String tripId,
                      String userId,
                      String name,
                      String avatarLetter,
                      int avatarColor,
                      String role) {
        this.tripId = tripId;
        this.userId = userId;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.role = role;
    }

    public String getTripId() {
        return tripId;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getAvatarLetter() {
        return avatarLetter;
    }

    public int getAvatarColor() {
        return avatarColor;
    }

    public String getRole() {
        return role;
    }

    public boolean isOwner() {
        return "OWNER".equalsIgnoreCase(role);
    }
}