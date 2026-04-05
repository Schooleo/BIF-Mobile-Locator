package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.place.PlaceResolveRequestDto;
import com.bif.app.core.network.dto.place.PlaceResolveResponseDto;
import com.bif.app.core.network.dto.place.PlaceReviewDto;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.core.utils.UserPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import android.content.SharedPreferences;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Response;

public class ReviewRepositoryTest {

    @Mock
    private ReviewDao mockReviewDao;
    @Mock
    private PlaceDao mockPlaceDao;
    @Mock
    private SyncQueueDao mockSyncQueueDao;
    @Mock
    private AppDatabase mockAppDatabase;
    @Mock
    private SyncManager mockSyncManager;
    @Mock
    private RestApiService mockApiService;
    @Mock
    private ExecutorService mockExecutor;
    @Mock
    private Context mockContext;

    private ReviewRepository repository;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        // Mock transaction execution to run immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockAppDatabase).runInTransaction(any(Runnable.class));

        // Mock executor to run immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockExecutor).execute(any(Runnable.class));

        // Fix NPE caused by UserPreferences static call in constructor
        SharedPreferences mockPrefs = mock(SharedPreferences.class);
        when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs);
        when(mockPrefs.getString(anyString(), anyString())).thenReturn("test-user");

        repository = new ReviewRepository(
                mockReviewDao,
                mockPlaceDao,
                mockSyncQueueDao,
                mockAppDatabase,
                mockSyncManager,
                mockApiService,
                mockExecutor,
                mockContext
        );
    }

    @Test
    public void resolveInternalPlaceId_WhenApiSucceeds_ReturnsInternalId() throws IOException {
        String internalId = "resolved-p1";
        PlaceResolveResponseDto mockRes = new PlaceResolveResponseDto();
        mockRes.internalPlaceId = internalId;
        
        Call<PlaceResolveResponseDto> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(mockRes));
        when(mockApiService.resolvePlace(any(PlaceResolveRequestDto.class))).thenReturn(mockCall);

        String result = repository.resolveInternalPlaceId("OSM", "ext-1", 1.0, 1.0, "Test");

        assertEquals(internalId, result);
        verify(mockApiService).resolvePlace(any(PlaceResolveRequestDto.class));
    }

    @Test
    public void submitReview_WhenCalled_UpsertsToRoomAndEnqueuesInSyncQueue() {
        String placeId = "p1";
        int stars = 5;
        String comment = "Epic!";

        repository.submitReview(placeId, stars, comment);

        // Verify Room persistence
        ArgumentCaptor<ReviewEntity> entityCaptor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(mockReviewDao).upsert(entityCaptor.capture());
        ReviewEntity saved = entityCaptor.getValue();
        assertEquals(placeId, saved.placeId);
        assertEquals(stars, saved.stars);
        assertTrue(saved.pendingSync);

        // Verify SyncQueue persistence
        ArgumentCaptor<SyncQueueEntity> syncCaptor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(syncCaptor.capture());
        SyncQueueEntity enqueued = syncCaptor.getValue();
        assertEquals("review", enqueued.entityType);
        assertEquals("CREATE", enqueued.operation);
        assertTrue(enqueued.payload.contains("\"rating\":5"));
        
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void refreshReviews_WhenApiSucceeds_UpsertsAllItemsToRoom() throws IOException {
        String placeId = "p1";
        List<PlaceReviewDto> body = new ArrayList<>();
        PlaceReviewDto dto = new PlaceReviewDto();
        dto.userId = "u1";
        dto.rating = 4;
        dto.comment = "Cool";
        body.add(dto);

        Call<List<PlaceReviewDto>> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(body));
        when(mockApiService.getPlaceReviews(placeId)).thenReturn(mockCall);

        repository.refreshReviews(placeId);

        verify(mockApiService).getPlaceReviews(placeId);
        verify(mockReviewDao).upsert(any(ReviewEntity.class));
    }

    private void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Condition expected to be true");
    }
}
