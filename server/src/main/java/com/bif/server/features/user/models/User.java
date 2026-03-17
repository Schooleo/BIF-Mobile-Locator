package com.bif.server.features.user.models;

import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "users")
public class User extends SyncDocument {
    @Id
    private String id;
    private String name;
    private String email;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOnline;
}
