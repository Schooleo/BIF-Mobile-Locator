package com.bif.app.domain.model;

public class Friend {
    private int id;
    private String serverUserId;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOnline;
    private long friendshipCreatedAt;

    public Friend(int id, String name, String avatarLetter, int avatarColor, boolean isOnline) {
        this(id, null, name, avatarLetter, avatarColor, isOnline, 0L);
    }

    public Friend(int id, String name, String avatarLetter, int avatarColor, boolean isOnline,
                  long friendshipCreatedAt) {
        this(id, null, name, avatarLetter, avatarColor, isOnline, friendshipCreatedAt);
    }

    public Friend(int id, String serverUserId, String name, String avatarLetter, int avatarColor,
                  boolean isOnline) {
        this(id, serverUserId, name, avatarLetter, avatarColor, isOnline, 0L);
    }

    public Friend(int id, String serverUserId, String name, String avatarLetter, int avatarColor,
                  boolean isOnline, long friendshipCreatedAt) {
        this.id = id;
        this.serverUserId = serverUserId;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOnline = isOnline;
        this.friendshipCreatedAt = friendshipCreatedAt;
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

    public long getFriendshipCreatedAt() {
        return friendshipCreatedAt;
    }

    public int getId() { return id; }
}
