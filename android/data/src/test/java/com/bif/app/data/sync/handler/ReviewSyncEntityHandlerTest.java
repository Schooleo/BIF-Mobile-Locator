package com.bif.app.data.sync.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ReviewSyncEntityHandlerTest {

    @Mock
    private ReviewDao mockReviewDao;
    
    private ReviewSyncEntityHandler handler;
    private final Gson gson = new Gson();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        handler = new ReviewSyncEntityHandler(mockReviewDao, gson);
    }

    @Test
    public void entityType_WhenCalled_ReturnsReview() {
        assertEquals("review", handler.entityType());
    }

    @Test
    public void applyPulledChange_WhenValidReviewJson_ParsesAndSavesToDao() {
        // Arrange
        String placeId = "p1";
        String userId = "u1";
        String payload = "{\"placeId\":\"" + placeId + "\", \"userId\":\"" + userId + "\", \"stars\":4, \"comment\":\"Nice\"}";
        
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "review";
        change.entityId = placeId + ":" + userId;
        change.operation = "CREATE";
        change.payload = payload;
        change.serverVersion = 100L;

        // Act
        handler.applyPulledChange(change, "current-user");

        // Assert
        ArgumentCaptor<ReviewEntity> captor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(mockReviewDao).upsert(captor.capture());
        
        ReviewEntity saved = captor.getValue();
        assertEquals(placeId, saved.placeId);
        assertEquals(userId, saved.userId);
        assertEquals(4, saved.stars);
        assertEquals("Nice", saved.comment);
        assertEquals(100L, saved.serverVersion);
        assertFalse(saved.pendingSync);
        assertFalse(saved.deleted);
    }
    
    private void assertFalse(boolean value) {
        if (value) throw new AssertionError("Expected false");
    }
}
