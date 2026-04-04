package com.bif.app.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.source.local.entity.UploadStatus;
import com.bif.app.data.sync.core.SyncManager;
import com.bif.app.domain.repository.IProfileRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.ExecutorService;

@RunWith(MockitoJUnitRunner.class)
public class ProfileRepositoryTest {

    @Mock
    private Context context;
    @Mock
    private ProfileDao profileDao;
    @Mock
    private SyncQueueDao syncQueueDao;
    @Mock
    private AppDatabase appDatabase;
    @Mock
    private SyncManager syncManager;
    @Mock
    private ExecutorService executorService;

    @Before
    public void setUp() {
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(appDatabase).runInTransaction(any(Runnable.class));

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
    }

    @Test
    public void constructor_withPersistedUserId_setsSyncContext() {
        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");

            new ProfileRepository(context, profileDao, syncQueueDao,
                    appDatabase, syncManager, executorService);

            verify(syncManager).setUserContext("user-123", null);
        }
    }

    @Test
    public void updateProfile_success_upsertsAndEnqueuesSync() {
        final boolean[] success = {false};
        final boolean[] failure = {false};

        ProfileEntity existing = new ProfileEntity();
        existing.userId = "user-123";
        existing.email = "alice@bif.com";
        existing.serverVersion = 7;
        existing.avatarColor = 99;
        existing.avatarLetter = "A";
        when(profileDao.getByUserId("user-123")).thenReturn(existing);

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context))
                    .thenReturn(true);
            prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");
            prefs.when(() -> UserPreferences.getEmail(context))
                    .thenReturn("alice@bif.com");

            ProfileRepository repository = new ProfileRepository(context,
                    profileDao, syncQueueDao, appDatabase,
                    syncManager, executorService);

            repository.updateProfile("Alice", new IProfileRepository
                    .ProfileCallback() {
                @Override
                public void onSuccess() {
                    success[0] = true;
                }

                @Override
                public void onFailure() {
                    failure[0] = true;
                }
            });

            ArgumentCaptor<ProfileEntity> entityCaptor = ArgumentCaptor
                    .forClass(ProfileEntity.class);
            verify(profileDao).upsert(entityCaptor.capture());
            ProfileEntity saved = entityCaptor.getValue();
            assertEquals("user-123", saved.userId);
            assertEquals("Alice", saved.displayName);
            assertEquals("alice@bif.com", saved.email);
            assertEquals(7, saved.serverVersion);

            ArgumentCaptor<SyncQueueEntity> queueCaptor = ArgumentCaptor
                    .forClass(SyncQueueEntity.class);
            verify(syncQueueDao).enqueue(queueCaptor.capture());
            SyncQueueEntity queued = queueCaptor.getValue();
            assertEquals("profile", queued.entityType);
            assertEquals("user-123", queued.entityId);
            assertEquals("UPDATE", queued.operation);

            verify(syncManager).syncIfOnline();
                prefs.verify(() -> UserPreferences.saveUserProfile(context,
                    "user-123", "Alice", "alice@bif.com"));

            assertTrue(success[0]);
            assertFalse(failure[0]);
        }
    }

    @Test
    public void updateProfile_blankInput_callsFailure() {
        final boolean[] failure = {false};

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");

            ProfileRepository repository = new ProfileRepository(context,
                    profileDao, syncQueueDao, appDatabase,
                    syncManager, executorService);

            repository.updateProfile("   ", new IProfileRepository
                    .ProfileCallback() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure() {
                    failure[0] = true;
                }
            });

            verify(syncQueueDao, never()).enqueue(any(SyncQueueEntity.class));
            verify(syncManager, never()).syncIfOnline();
            assertTrue(failure[0]);
        }
    }

    @Test
    public void syncProfileMetadata_offline_callsSuccess() {
        final boolean[] success = {false};
        final boolean[] failure = {false};

        when(syncManager.sync()).thenReturn(null);
        when(syncManager.isOnline()).thenReturn(false);

        try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
            prefs.when(() -> UserPreferences.isLoggedIn(context))
                    .thenReturn(true);
            prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");

            ProfileRepository repository = new ProfileRepository(context,
                    profileDao, syncQueueDao, appDatabase,
                    syncManager, executorService);

            repository.syncProfileMetadata(new IProfileRepository
                    .ProfileCallback() {
                @Override
                public void onSuccess() {
                    success[0] = true;
                }

                @Override
                public void onFailure() {
                    failure[0] = true;
                }
            });

            verify(syncManager).sync();
            assertTrue(success[0]);
            assertFalse(failure[0]);
        }
    }

            @Test
            public void readLocalProfile_whenDaoThrowsMainThreadException_fallsBackToPreferences() {
            when(profileDao.getByUserId("user-123"))
                .thenThrow(new IllegalStateException("main thread query"));

            try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
                prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");
                prefs.when(() -> UserPreferences.isLoggedIn(context))
                    .thenReturn(true);
                prefs.when(() -> UserPreferences.getUsername(context))
                    .thenReturn("pref-user");
                prefs.when(() -> UserPreferences.getEmail(context))
                    .thenReturn("pref@bif.com");
                prefs.when(() -> UserPreferences.getAvatarUri(context))
                    .thenReturn("pref-avatar");

                ProfileRepository repository = new ProfileRepository(context,
                    profileDao, syncQueueDao, appDatabase,
                    syncManager, executorService);

                IProfileRepository.LocalProfile profile = repository.readLocalProfile();
                assertTrue(profile.isLoggedIn);
                assertEquals("pref-user", profile.username);
                assertEquals("pref@bif.com", profile.email);
                assertEquals("pref-avatar", profile.avatarUri);
            }
            }

            @Test
            public void readLocalProfile_prefersPendingLocalAvatarOverRemoteUrl() {
            ProfileEntity entity = new ProfileEntity();
            entity.userId = "user-123";
            entity.displayName = "Alice";
            entity.email = "alice@bif.com";
            entity.avatarUrl = "https://cdn.example/avatar.jpg";
            entity.localImagePath = "/data/user/0/com.bif.app/files/image-staging/avatar-1.jpg";
            entity.uploadStatus = UploadStatus.PENDING;

            when(profileDao.getByUserId("user-123")).thenReturn(entity);

            try (MockedStatic<UserPreferences> prefs = org.mockito.Mockito
                .mockStatic(UserPreferences.class)) {
                prefs.when(() -> UserPreferences.getUserId(context))
                    .thenReturn("user-123");
                prefs.when(() -> UserPreferences.isLoggedIn(context))
                    .thenReturn(true);
                prefs.when(() -> UserPreferences.getUsername(context))
                    .thenReturn("pref-user");
                prefs.when(() -> UserPreferences.getEmail(context))
                    .thenReturn("pref@bif.com");
                prefs.when(() -> UserPreferences.getAvatarUri(context))
                    .thenReturn("pref-avatar");

                ProfileRepository repository = new ProfileRepository(context,
                    profileDao, syncQueueDao, appDatabase,
                    syncManager, executorService);

                IProfileRepository.LocalProfile profile = repository.readLocalProfile();
                assertEquals("Alice", profile.username);
                assertEquals("alice@bif.com", profile.email);
                assertEquals(entity.localImagePath, profile.avatarUri);
            }
            }
}

