package com.bif.server.features.friendship.dto;

import lombok.Data;

@Data
public class FriendshipApiModel {
    private String id;
    private String requesterId;
    private String requesterName;
    private String receiverId;
    private String status;
    private String createdAt;
    private String updatedAt;
}
