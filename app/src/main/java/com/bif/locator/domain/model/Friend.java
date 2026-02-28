package com.bif.locator.domain.model;

public class Friend {
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOnline;

    public Friend(String name, String avatarLetter, int avatarColor, boolean isOnline) {
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOnline = isOnline;
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

    public boolean isOnline() {
        return isOnline;
    }
}
