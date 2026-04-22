package com.bif.server.features.favorite.models;

import com.bif.server.common.models.Location;
import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "favorites")
public class Favorite extends SyncDocument {
    @Id
    private String id;
    private String placeId;
    private String externalSource;
    private String externalId;
    private String placeName;
    private String name;
    private Location location;
    private String address;
    private String description;
    private String notes;
    private int rating;
    private String imagePath;

    private String userId;
}
