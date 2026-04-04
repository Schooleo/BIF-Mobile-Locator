package com.bif.server.features.place.models;

import com.bif.server.common.models.Location;
import com.bif.server.common.models.SyncDocument;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "places")
public class Place extends SyncDocument {
    @Id
    private String id;
    private String name;
    private String address;
    private double rating;
    private Location location;
    private List<String> tags;
    private String placeSource;
    private String persistedByAction;
    private String persistedByUserId;
    private int reviewCount;
    private String photoUrl;
    private List<PlaceReview> reviews;
}
