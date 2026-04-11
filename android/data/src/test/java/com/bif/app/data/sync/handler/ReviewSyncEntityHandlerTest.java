package com.bif.app.data.sync.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.PlaceEntity;
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

    @Mock
    private PlaceDao mockPlaceDao;

    @Mock
    private SyncQueueDao mockSyncQueueDao;

    @Mock
    private AppDatabase mockAppDatabase;
    
    private ReviewSyncEntityHandler handler;
    private final Gson gson = new Gson();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        org.mockito.Mockito.doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockAppDatabase).runInTransaction(any(Runnable.class));

        when(mockPlaceDao.getByIdSync(any(), any())).thenAnswer(invocation -> {
            PlaceEntity place = new PlaceEntity();
            place.id = invocation.getArgument(0);
            place.ownerUserId = invocation.getArgument(1);
            return place;
        });

        handler = new ReviewSyncEntityHandler(
                mockReviewDao,
                mockPlaceDao,
                mockSyncQueueDao,
                mockAppDatabase,
                gson);
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

    @Test
    public void applyPulledChange_WhenPlaceIdCorrected_HealsOldIdentityAndCleansQueue() {
        String payload = "{\"placeId\":\"REAL_ID\",\"userId\":\"u1\",\"stars\":5,\"comment\":\"Great\"}";

        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "review";
        change.entityId = "GHOST_ID:u1";
        change.operation = "UPDATE";
        change.payload = payload;
        change.serverVersion = 200L;

        handler.applyPulledChange(change, "owner-1");

        verify(mockReviewDao).deleteByPlaceAndUserId("GHOST_ID", "u1");
        verify(mockSyncQueueDao).removeByEntity("review", "GHOST_ID:u1");
        verify(mockReviewDao, atLeastOnce()).getByPlaceIdSync("GHOST_ID");
        verify(mockReviewDao, atLeastOnce()).getByPlaceIdSync("REAL_ID");

        ArgumentCaptor<ReviewEntity> captor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(mockReviewDao, atLeastOnce()).upsert(captor.capture());
        ReviewEntity saved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals("REAL_ID", saved.placeId);
        assertEquals("u1", saved.userId);
        assertEquals(5, saved.stars);
        assertFalse(saved.pendingSync);
        assertFalse(saved.deleted);

        verify(mockPlaceDao, atLeastOnce()).getByIdSync(eq("GHOST_ID"), eq("owner-1"));
        verify(mockPlaceDao, atLeastOnce()).getByIdSync(eq("REAL_ID"), eq("owner-1"));
    }
}
