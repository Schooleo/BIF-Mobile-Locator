package com.bif.server.features.place.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "place_reviews")
@CompoundIndex(name = "uk_place_review_place_user", def = "{'placeId': 1, 'userId': 1}", unique = true)
public class PlaceReview {
    @Id
    private String id;
    private String userId;
    private String placeId;
    private int stars;
    private String comment;
    private LocalDateTime createdAt;
}
