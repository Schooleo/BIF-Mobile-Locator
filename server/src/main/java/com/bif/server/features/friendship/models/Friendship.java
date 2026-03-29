package com.bif.server.features.friendship.models;

import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "friendships")
public class Friendship extends SyncDocument {
    @Id
    private String id;
    private String requesterId;
    private String receiverId;
    private FriendshipStatus status;
    
    @CreatedDate
    private Instant createdAt;
}
