package com.bif.app.data.mapper;

import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.bif.app.domain.model.Review;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReviewMapper {

    public static ReviewEntity toEntity(Review review) {
        if (review == null) return null;
        ReviewEntity entity = new ReviewEntity();
        entity.placeId = review.placeId;
        entity.userId = review.userId;
        entity.userName = review.userName;
        entity.stars = review.stars;
        entity.comment = review.comment;
        entity.createdAt = review.createdAt;
        entity.externalSource = review.externalSource;
        entity.externalId = review.externalId;
        entity.lat = review.lat;
        entity.lng = review.lng;
        entity.placeName = review.placeName;
        entity.serverVersion = review.serverVersion;
        entity.deleted = review.deleted;
        entity.lastSyncedAt = review.lastSyncedAt;
        entity.pendingSync = review.pendingSync;
        return entity;
    }

    public static Review toDomain(ReviewEntity entity) {
        if (entity == null) return null;
        Review review = new Review();
        review.placeId = entity.placeId;
        review.userId = entity.userId;
        review.userName = entity.userName;
        review.stars = entity.stars;
        review.comment = entity.comment;
        review.createdAt = entity.createdAt;
        review.externalSource = entity.externalSource;
        review.externalId = entity.externalId;
        review.lat = entity.lat;
        review.lng = entity.lng;
        review.placeName = entity.placeName;
        review.serverVersion = entity.serverVersion;
        review.deleted = entity.deleted;
        review.lastSyncedAt = entity.lastSyncedAt;
        review.pendingSync = entity.pendingSync;
        return review;
    }
    
    public static List<Review> toDomainList(List<ReviewEntity> entities) {
        if (entities == null) return new ArrayList<>();
        List<Review> list = new ArrayList<>();
        for (ReviewEntity e : entities) {
            list.add(toDomain(e));
        }
        return list;
    }

    public static ReviewEntity fromDto(PlaceReviewDto dto, String placeId) {
        if (dto == null) return null;
        ReviewEntity entity = new ReviewEntity();
        entity.placeId = placeId;
        entity.userId = dto.userId;
        entity.userName = dto.userName;
        entity.stars = dto.stars;
        entity.comment = dto.comment;
        entity.externalSource = dto.externalSource;
        entity.externalId = dto.externalId;
        entity.lat = dto.lat;
        entity.lng = dto.lng;
        entity.placeName = dto.placeName;
        entity.createdAt = dto.createdAt;

        entity.serverVersion = dto.serverVersion;
        if (dto.updatedAt != null && !dto.updatedAt.isEmpty()) {
            try {
                entity.lastSyncedAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dto.updatedAt)).toEpochMilli();
            } catch (Exception e) {}
        }
        
        // fromDto typically comes from server, so it's not pending sync
        entity.pendingSync = false;
        entity.deleted = false;
        return entity;
    }

    public static PlaceReviewDto toDto(Review review) {
        if (review == null) return null;
        PlaceReviewDto dto = new PlaceReviewDto();
        dto.placeId = review.placeId;
        dto.userId = review.userId;
        dto.userName = review.userName;
        dto.stars = review.stars;
        dto.comment = review.comment;
        dto.externalSource = review.externalSource;
        dto.externalId = review.externalId;
        dto.lat = review.lat;
        dto.lng = review.lng;
        dto.placeName = review.placeName;
        dto.createdAt = review.createdAt;
        return dto;
    }
}
