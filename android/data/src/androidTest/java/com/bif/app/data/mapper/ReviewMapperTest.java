package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.bif.app.domain.model.Review;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ReviewMapperTest {

    @Test
    public void fromDto_WhenDtoContainsSyncMetadata_PreservesServerVersionAndLastSyncedAt() {
        PlaceReviewDto dto = new PlaceReviewDto();
        dto.userId = "u1";
        dto.userName = "Alice";
        dto.rating = 4;
        dto.comment = "Nice!";
        dto.serverVersion = 42;
        dto.updatedAt = "2023-04-05T17:00:00Z";

        ReviewEntity entity = ReviewMapper.fromDto(dto, "p1");

        assertEquals("p1", entity.placeId);
        assertEquals("u1", entity.userId);
        assertEquals(42, entity.serverVersion);
        // 2023-04-05T17:00:00Z is 1680714000000L ms since epoch
        assertEquals(1680714000000L, entity.lastSyncedAt);
    }

    @Test
    public void toDomain_WhenCommentIsNull_HandlesGracefully() {
        ReviewEntity entity = new ReviewEntity();
        entity.placeId = "p1";
        entity.userId = "u1";
        entity.comment = null;
        entity.stars = 3;

        Review domain = ReviewMapper.toDomain(entity);

        assertEquals("p1", domain.placeId);
        assertEquals("u1", domain.userId);
        assertEquals(3, domain.stars);
        assertNull(domain.comment);
    }
}
