package com.bif.server.features.place.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "place_reviews")
@CompoundIndexes({
    @CompoundIndex(name = "uk_place_review_place_user", def = "{'placeId': 1, 'userId': 1}", unique = true),
    @CompoundIndex(name = "idx_place_review_place_created_at_desc", def = "{'placeId': 1, 'createdAt': -1}")
})
public class PlaceReview {
    @Id
    private String id;
    private String userId;
    private String placeId;
    private int stars;
    private String comment;
    private String externalSource;
    private String externalId;
    private Double lat;
    private Double lng;
    private String placeName;
    private Instant createdAt;
}
