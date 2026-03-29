package com.bif.app.domain.model;

public class Friendship {
    private final int id;
    private final String serverId;
    private final String requesterId;
    private final String requesterName;
    private final String receiverId;
    private final FriendshipStatus status;
    private final long createdAt;
    private final long updatedAt;

    public Friendship(int id,
                      String serverId,
                      String requesterId,
                      String requesterName,
                      String receiverId,
                      FriendshipStatus status,
                      long createdAt,
                      long updatedAt) {
        this.id = id;
        this.serverId = serverId;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.receiverId = receiverId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public String getServerId() {
        return serverId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
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