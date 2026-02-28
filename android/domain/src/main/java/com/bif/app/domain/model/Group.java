package com.bif.app.domain.model;

public class Group {
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private int memberCount;

    public Group(String name, String avatarLetter, int avatarColor, int memberCount) {
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.memberCount = memberCount;
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

    public int getMemberCount() {
        return memberCount;
    }
}
