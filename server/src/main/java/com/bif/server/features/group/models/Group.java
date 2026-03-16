package com.bif.server.features.group.models;

import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "groups")
public class Group extends SyncDocument {
    @Id
    private String id;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private int memberCount;
    private List<String> memberIds;
    private String ownerId;
}
