package com.bif.app.domain.model;

public class Friendship {
    private final int id;
    private final String requesterId;
    private final String receiverId;
    private final FriendshipStatus status;
    private final long createdAt;
    private final long updatedAt;

    public Friendship(int id,
                      String requesterId,
                      String receiverId,
                      FriendshipStatus status,
                      long createdAt,
                      long updatedAt) {
        this.id = id;
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public FriendshipStatus getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}