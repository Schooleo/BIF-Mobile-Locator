package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.data.LiveDataTestUtil;
import com.bif.app.data.source.AndroidGeocodingDataSource;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Response;

public class FavoriteRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private FavoriteDao mockDao;
    @Mock
    private SyncQueueDao mockSyncQueueDao;
    @Mock
    private AppDatabase mockAppDatabase;
    @Mock
    private RestApiService mockRestApiService;
    @Mock
    private SyncManager mockSyncManager;
    @Mock
    private ExecutorService mockExecutorService;
    @Mock
    private PlaceDao mockPlaceDao;
    @Mock
    private AndroidGeocodingDataSource mockGeocodingDataSource;

    private FavoriteRepository repository;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        
        // Mock transaction execution
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockAppDatabase).runInTransaction(any(Runnable.class));

        // Mock executor execution
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mockExecutorService).execute(any(Runnable.class));

        when(mockSyncManager.isOnline()).thenReturn(true);

        repository = new FavoriteRepository(
                mockDao, 
                mockSyncQueueDao, 
                mockAppDatabase, 
            mockRestApiService,
                mockSyncManager, 
            mockExecutorService,
            null
        );
    }

    @Test
    public void constructor_withApplicationContext_doesNotSetSyncContextEagerly() {
        Context appContext = org.mockito.Mockito.mock(Context.class);
        try (MockedStatic<UserPreferences> userPrefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            userPrefs.when(() -> UserPreferences.getUserId(appContext))
                    .thenReturn("user-123");

            new FavoriteRepository(
                    mockDao,
                    mockSyncQueueDao,
                    mockAppDatabase,
                    mockRestApiService,
                    mockSyncManager,
                    mockExecutorService,
                    appContext
            );

            verify(mockSyncManager, never()).setUserContext(any(), any());
        }
    }

    @Test
    public void getAllFavorites_DaoReturnsEntities_ReturnsMappedDomains() throws InterruptedException {
        // Arrange
        List<FavoriteEntity> mockEntities = new ArrayList<>();
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = "fav-1";
        entity.name = "Test Place";
        mockEntities.add(entity);

        MutableLiveData<List<FavoriteEntity>> fakeLiveData = new MutableLiveData<>();
        fakeLiveData.setValue(mockEntities);

        when(mockDao.getAll("anonymous")).thenReturn(fakeLiveData);

        // Act
        List<Favorite> result = LiveDataTestUtil.getOrAwaitValue(repository.getAllFavorites());

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test Place", result.get(0).name);
        assertEquals("fav-1", result.get(0).id);
    }

    @Test
    public void addFavorite_Success_InsertsAndEnqueuesInTransaction() {
        // Arrange
        Favorite domainItem = new Favorite();
        domainItem.id = "fav-10";
        domainItem.name = "Cafe";

        // Act
        repository.addFavorite(domainItem);

        // Assert
        verify(mockAppDatabase).runInTransaction(any(Runnable.class));
        verify(mockDao).insert(any(FavoriteEntity.class));
        
        ArgumentCaptor<SyncQueueEntity> captor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(captor.capture());
        
        SyncQueueEntity enqueued = captor.getValue();
        assertEquals("favorite", enqueued.entityType);
        assertEquals("fav-10", enqueued.entityId);
        assertEquals("CREATE", enqueued.operation);
        
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void addFavorite_WhenDomainIdMissing_UsesGeneratedIdForSyncEntry() {
        // Arrange
        Favorite domainItem = new Favorite();
        domainItem.name = "No Id Yet";

        // Act
        repository.addFavorite(domainItem);

        // Assert persisted entity has generated id
        ArgumentCaptor<FavoriteEntity> entityCaptor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(mockDao).insert(entityCaptor.capture());
        FavoriteEntity inserted = entityCaptor.getValue();
        assertNotNull(inserted.id);
        assertTrue(!inserted.id.trim().isEmpty());

        // Assert sync queue uses the generated id (not null domain id)
        ArgumentCaptor<SyncQueueEntity> syncCaptor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(syncCaptor.capture());
        SyncQueueEntity queued = syncCaptor.getValue();
        assertEquals(inserted.id, queued.entityId);
        assertNotNull(queued.payload);
        assertTrue(queued.payload.contains("\"id\":\"" + inserted.id + "\""));

        // Domain object is updated with generated id for consistency in caller flow.
        assertEquals(inserted.id, domainItem.id);
    }

    @Test
    public void updateFavorite_Success_UpdatesAndEnqueuesInTransaction() {
        // Arrange
        Favorite domainItem = new Favorite();
        domainItem.id = "fav-20";
        domainItem.name = "New Name";

        // Act
        repository.updateFavorite(domainItem);

        // Assert
        verify(mockAppDatabase).runInTransaction(any(Runnable.class));
        verify(mockDao).update(any(FavoriteEntity.class));
        
        ArgumentCaptor<SyncQueueEntity> captor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(captor.capture());
        
        assertEquals("UPDATE", captor.getValue().operation);
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void deleteFavorite_Success_SoftDeletesAndEnqueuesInTransaction() {
        // Arrange
        Favorite domainItem = new Favorite();
        domainItem.id = "fav-30";

        // Act
        repository.deleteFavorite(domainItem);

        // Assert
        verify(mockAppDatabase).runInTransaction(any(Runnable.class));
        
        ArgumentCaptor<FavoriteEntity> entityCaptor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(mockDao).update(entityCaptor.capture());
        
        // Assert SOFT DELETE
        assertEquals(true, entityCaptor.getValue().deleted);
        
        ArgumentCaptor<SyncQueueEntity> syncCaptor = ArgumentCaptor.forClass(SyncQueueEntity.class);
        verify(mockSyncQueueDao).enqueue(syncCaptor.capture());
        
        assertEquals("DELETE", syncCaptor.getValue().operation);
        verify(mockSyncManager).syncIfOnline();
    }

    @Test
    public void refreshFavorites_CallsRemoteBootstrapWithoutForcedSync() throws Exception {
        Context appContext = org.mockito.Mockito.mock(Context.class);
        try (MockedStatic<UserPreferences> userPrefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            userPrefs.when(() -> UserPreferences.getUserId(appContext))
                    .thenReturn("user-123");

            repository = new FavoriteRepository(
                    mockDao,
                    mockSyncQueueDao,
                    mockAppDatabase,
                    mockRestApiService,
                    mockSyncManager,
                    mockExecutorService,
                    appContext
            );

            @SuppressWarnings("unchecked")
            Call<List<FavoriteResponseDto>> call = org.mockito.Mockito.mock(Call.class);
            when(mockRestApiService.getMyFavorites()).thenReturn(call);
            when(mockSyncManager.isOnline()).thenReturn(true);
            when(call.execute()).thenReturn(Response.success(new ArrayList<>()));

            // Act
            repository.refreshFavorites(null);

            // Assert
                verify(mockSyncManager, org.mockito.Mockito.atLeastOnce())
                    .setUserContext("user-123", null);
            verify(mockRestApiService).getMyFavorites();
        }
    }

    @Test
    public void refreshFavorites_WhenBootstrapFails_ReportsOfflineError() throws Exception {
        Context appContext = org.mockito.Mockito.mock(Context.class);
        try (MockedStatic<UserPreferences> userPrefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            userPrefs.when(() -> UserPreferences.getUserId(appContext))
                    .thenReturn("user-123");

            repository = new FavoriteRepository(
                    mockDao,
                    mockSyncQueueDao,
                    mockAppDatabase,
                    mockRestApiService,
                    mockSyncManager,
                    mockExecutorService,
                    appContext
            );

            // Arrange
            @SuppressWarnings("unchecked")
            Call<List<FavoriteResponseDto>> call = org.mockito.Mockito.mock(Call.class);
            when(mockRestApiService.getMyFavorites()).thenReturn(call);
            when(mockSyncManager.isOnline()).thenReturn(true);
            when(call.execute()).thenThrow(new java.io.IOException("offline"));

            final boolean[] success = {false};
            final String[] errorMessage = {null};

            // Act
            repository.refreshFavorites(new IFavoriteRepository.SyncCallback() {
                @Override
                public void onSuccess() {
                    success[0] = true;
                }

                @Override
                public void onOffline() {
                    // no-op
                }

                @Override
                public void onError(String message) {
                    errorMessage[0] = message;
                }
            });

            // Assert - Bootstrap failure maps to generic error key.
            assertFalse(success[0]);
            assertNotNull(errorMessage[0]);
            assertEquals(IFavoriteRepository.ERROR_REFRESH_FAILED, errorMessage[0]);
        }
    }

    @Test
    public void refreshFavorites_WhenDeviceOffline_UsesOfflineCallbackWithoutRemoteCall() {
        Context appContext = org.mockito.Mockito.mock(Context.class);
        try (MockedStatic<UserPreferences> userPrefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            userPrefs.when(() -> UserPreferences.getUserId(appContext))
                    .thenReturn("user-123");

            repository = new FavoriteRepository(
                    mockDao,
                    mockSyncQueueDao,
                    mockAppDatabase,
                    mockRestApiService,
                    mockSyncManager,
                    mockExecutorService,
                    appContext
            );

            when(mockSyncManager.isOnline()).thenReturn(false);

            final boolean[] offline = {false};
            final boolean[] success = {false};

            repository.refreshFavorites(new IFavoriteRepository.SyncCallback() {
                @Override
                public void onSuccess() {
                    success[0] = true;
                }

                @Override
                public void onOffline() {
                    offline[0] = true;
                }

                @Override
                public void onError(String message) {
                    // no-op
                }
            });

            assertTrue(offline[0]);
            assertFalse(success[0]);
            verify(mockRestApiService, never()).getMyFavorites();
        }
    }

    @Test
    public void refreshFavorites_WhenFavoriteHasPlaceholderMetadata_ReconcilesFromReverseGeocode() {
        FavoriteEntity placeholder = new FavoriteEntity();
        placeholder.id = "fav-99";
        placeholder.placeId = "place-99";
        placeholder.name = "Unnamed place";
        placeholder.placeName = "Unnamed place";
        placeholder.address = "Address unavailable";
        placeholder.latitude = 10.0;
        placeholder.longitude = 106.0;
        placeholder.userId = "anonymous";

        when(mockDao.getAllSync("anonymous")).thenReturn(Collections.singletonList(placeholder));
        when(mockPlaceDao.getByIdSync("place-99", "anonymous")).thenReturn(null);

        android.location.Address address = org.mockito.Mockito.mock(android.location.Address.class);
        when(address.getFeatureName()).thenReturn("St. Joseph Medical Clinic");
        when(address.getAddressLine(0)).thenReturn("12 Nguyen Trai, District 1, Ho Chi Minh City");
        when(mockGeocodingDataSource.reverseGeocodeLocation(10.0, 106.0))
                .thenReturn(Collections.singletonList(address));

        FavoriteRepository reconciliatingRepository = new FavoriteRepository(
                mockDao,
                mockSyncQueueDao,
                mockAppDatabase,
                mockPlaceDao,
                mockGeocodingDataSource,
                mockRestApiService,
                mockSyncManager,
                mockExecutorService,
                null);

        reconciliatingRepository.refreshFavorites(null);

        verify(mockGeocodingDataSource).reverseGeocodeLocation(10.0, 106.0);
        ArgumentCaptor<FavoriteEntity> captor = ArgumentCaptor.forClass(FavoriteEntity.class);
        verify(mockDao).update(captor.capture());
        FavoriteEntity updated = captor.getValue();
        assertEquals("St. Joseph Medical Clinic", updated.name);
        assertEquals("St. Joseph Medical Clinic", updated.placeName);
        assertEquals("12 Nguyen Trai, District 1, Ho Chi Minh City", updated.address);
        assertEquals(Place.SOURCE_OSM, updated.externalSource);
        verify(mockSyncQueueDao).enqueue(any(SyncQueueEntity.class));
    }
}
