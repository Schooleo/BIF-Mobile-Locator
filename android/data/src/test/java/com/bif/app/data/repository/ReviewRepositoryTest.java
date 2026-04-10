package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import org.junit.Rule;

public class ReviewRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

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
        
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockAppDatabase).runInTransaction(any(Runnable.class));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockExecutor).execute(any(Runnable.class));

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
    public void resolveInternalPlaceId_WhenApiFails_ReturnsDeterministicFallback() throws IOException {
        Call<PlaceResolveResponseDto> mockCall = mock(Call.class);
        when(mockCall.execute()).thenThrow(new IOException("offline"));
        when(mockApiService.resolvePlace(any(PlaceResolveRequestDto.class))).thenReturn(mockCall);

        String first = repository.resolveInternalPlaceId("OSM", "ext-1", 1.0, 1.0, "Test");
        String second = repository.resolveInternalPlaceId("OSM", "ext-1", 1.0, 1.0, "Test");
        String third = repository.resolveInternalPlaceId("OSM", "ext-1", 1.0, 1.0, "Other");

        assertEquals(first, second);
        assertNotEquals(first, third);
    }

    @Test
    public void submitReview_WhenCalled_UpsertsToRoomAndEnqueuesInSyncQueue() {
        String placeId = "p1";
        int stars = 5;
        String comment = "Epic!";

        repository.submitReview(placeId, stars, comment);

        ArgumentCaptor<ReviewEntity> entityCaptor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(mockReviewDao).upsert(entityCaptor.capture());
        ReviewEntity saved = entityCaptor.getValue();
        assertEquals(placeId, saved.placeId);
        assertEquals(stars, saved.stars);
        assertTrue(saved.pendingSync);

        ArgumentCaptor<SyncQueueEntity> syncCaptor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(syncCaptor.capture());
        SyncQueueEntity enqueued = syncCaptor.getValue();
        assertEquals("review", enqueued.entityType);
        assertEquals("CREATE", enqueued.operation);
        assertTrue(enqueued.payload.contains("\"rating\":5"));
        
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void submitReview_WhenOnline_PersistsLocallyAndWritesThroughServer() throws IOException {
        String placeId = "p1";
        int stars = 5;
        String comment = "";

        when(mockSyncManager.isOnline()).thenReturn(true);

        PlaceReviewDto responseDto = new PlaceReviewDto();
        responseDto.placeId = placeId;
        responseDto.userId = "test-user";
        responseDto.userName = "tester";
        responseDto.stars = stars;
        responseDto.comment = null;
        responseDto.createdAt = "2026-04-10T00:00:00Z";

        Call<PlaceReviewDto> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(responseDto));
        when(mockApiService.addReview(anyString(), any(PlaceReviewDto.class))).thenReturn(mockCall);

        repository.submitReview(placeId, stars, comment);

        verify(mockApiService).addReview(eq(placeId), any(PlaceReviewDto.class));
        verify(mockSyncQueueDao).removeByEntity("review", placeId + ":test-user");
        verify(mockSyncQueueDao, never()).enqueue(any(SyncQueueEntity.class));

        ArgumentCaptor<ReviewEntity> entityCaptor = ArgumentCaptor.forClass(ReviewEntity.class);
        verify(mockReviewDao, org.mockito.Mockito.atLeastOnce()).upsert(entityCaptor.capture());
        ReviewEntity saved = entityCaptor.getValue();
        assertEquals(placeId, saved.placeId);
        assertEquals("test-user", saved.userId);
        assertEquals(stars, saved.stars);
        org.junit.Assert.assertFalse(saved.pendingSync);
    }

    @Test
    public void deleteMyReview_WhenOnline_DeletesServerAndLocalEntry() throws IOException {
        String placeId = "p1";

        when(mockSyncManager.isOnline()).thenReturn(true);

        ReviewEntity existing = new ReviewEntity();
        existing.placeId = placeId;
        existing.userId = "test-user";
        existing.stars = 4;
        when(mockReviewDao.getReviewSync(placeId, "test-user")).thenReturn(existing);

        Call<Void> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(null));
        when(mockApiService.deleteMyReview(placeId)).thenReturn(mockCall);

        repository.deleteMyReview(placeId);

        verify(mockApiService).deleteMyReview(placeId);
        verify(mockReviewDao).deleteByPlaceAndUserId(placeId, "test-user");
        verify(mockSyncQueueDao).removeByEntity("review", placeId + ":test-user");
        verify(mockSyncQueueDao, never()).enqueue(any(SyncQueueEntity.class));
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

    @Test
    public void refreshReviews_ReconcilesAndDeletesOnlyMissingNonPendingLocalReviews() throws IOException {
        String placeId = "p1";

        List<PlaceReviewDto> serverBody = new ArrayList<>();
        PlaceReviewDto serverReview = new PlaceReviewDto();
        serverReview.userId = "u1";
        serverReview.rating = 5;
        serverReview.comment = "Great";
        serverBody.add(serverReview);

        Call<List<PlaceReviewDto>> mockCall = mock(Call.class);
        when(mockCall.execute()).thenReturn(Response.success(serverBody));
        when(mockApiService.getPlaceReviews(placeId)).thenReturn(mockCall);
        when(mockReviewDao.getReviewSync(placeId, "u1")).thenReturn(null);

        ReviewEntity localInServer = new ReviewEntity();
        localInServer.placeId = placeId;
        localInServer.userId = "u1";
        localInServer.pendingSync = false;

        ReviewEntity localPendingOnly = new ReviewEntity();
        localPendingOnly.placeId = placeId;
        localPendingOnly.userId = "u2";
        localPendingOnly.pendingSync = true;

        ReviewEntity localMissingAndSynced = new ReviewEntity();
        localMissingAndSynced.placeId = placeId;
        localMissingAndSynced.userId = "u3";
        localMissingAndSynced.pendingSync = false;

        List<ReviewEntity> localReviews = new ArrayList<>();
        localReviews.add(localInServer);
        localReviews.add(localPendingOnly);
        localReviews.add(localMissingAndSynced);
        when(mockReviewDao.getByPlaceIdSync(placeId)).thenReturn(localReviews);

        repository.refreshReviews(placeId);

        verify(mockReviewDao).deleteByPlaceAndUserId(placeId, "u3");
        verify(mockReviewDao, never()).deleteByPlaceAndUserId(placeId, "u1");
        verify(mockReviewDao, never()).deleteByPlaceAndUserId(placeId, "u2");
    }
}
