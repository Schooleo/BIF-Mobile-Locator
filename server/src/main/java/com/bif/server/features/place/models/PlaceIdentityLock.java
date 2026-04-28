package com.bif.server.features.place.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "place_identity_locks")
public class PlaceIdentityLock {
    @Id
    private String id;

    private String ownerToken;
    private Instant acquiredAt;
    private Instant createdAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
