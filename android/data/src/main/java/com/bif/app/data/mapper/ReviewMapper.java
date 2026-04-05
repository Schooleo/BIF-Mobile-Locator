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
        entity.stars = dto.getEffectiveStars(); 
        entity.comment = dto.comment;
        
        if (dto.createdAt != null && !dto.createdAt.isEmpty()) {
            try {
                entity.createdAt = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(dto.createdAt)).toEpochMilli();
            } catch (Exception e) {
                entity.createdAt = System.currentTimeMillis();
            }
        } else {
            entity.createdAt = System.currentTimeMillis();
        }

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
        dto.rating = review.stars;
        dto.stars = review.stars;
        dto.comment = review.comment;
        dto.createdAt = Instant.ofEpochMilli(review.createdAt).toString();
        return dto;
    }
}
