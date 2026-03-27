package com.bif.app.domain.model;

public class Friend {
    private int id;
    private String serverUserId;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOnline;

    public Friend(int id, String name, String avatarLetter, int avatarColor, boolean isOnline) {
        this(id, null, name, avatarLetter, avatarColor, isOnline);
    }

    public Friend(int id, String serverUserId, String name, String avatarLetter, int avatarColor, boolean isOnline) {
        this.id = id;
        this.serverUserId = serverUserId;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOnline = isOnline;
    }

    public String getServerUserId() {
        return serverUserId;
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

    public int getId() { return id; }
}
