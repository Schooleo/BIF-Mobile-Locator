package com.bif.app.di;

import android.content.Context;

import androidx.room.Room;

import com.bif.app.core.auth.LocalSessionDataCleaner;
import com.bif.app.data.source.local.database.AppDatabase;
import com.bif.app.data.source.local.dao.ChatMessageDao;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.dao.FriendshipDao;
import com.bif.app.data.source.local.dao.GroupDao;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.SearchHistoryDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.dao.TripDao;

import javax.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import timber.log.Timber;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    private static final String TAG = "AppModule";

    @Provides
    @Singleton
    public ExecutorService provideExecutorService() {
        return Executors.newFixedThreadPool(4);
    }

    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                context,
                AppDatabase.class,
                "bif_database")
            .addMigrations(AppDatabase.MIGRATION_15_16)
                .build();
    }

    @Provides
    @Singleton
    public FavoriteDao provideFavoriteDao(AppDatabase database) {
        return database.favoriteDao();
    }

    @Provides
    @Singleton
    public FriendDao provideFriendDao(AppDatabase database) {
        return database.friendDao();
    }

    @Provides
    @Singleton
    public FriendshipDao provideFriendshipDao(AppDatabase database) {
        return database.friendshipDao();
    }

    @Provides
    @Singleton
    public GroupDao provideGroupDao(AppDatabase database) {
        return database.groupDao();
    }

    @Provides
    @Singleton
    public PlaceDao providePlaceDao(AppDatabase database) {
        return database.placeDao();
    }

    @Provides
    @Singleton
    public ProfileDao provideProfileDao(AppDatabase database) {
        return database.profileDao();
    }

    @Provides
    @Singleton
    public SearchHistoryDao provideSearchHistoryDao(AppDatabase database) {
        return database.searchHistoryDao();
    }

    @Provides
    @Singleton
    public SyncQueueDao provideSyncQueueDao(AppDatabase database) {
        return database.syncQueueDao();
    }

    @Provides
    @Singleton
    public ChatMessageDao provideChatMessageDao(AppDatabase database) {
        return database.chatMessageDao();
    }

    @Provides
    @Singleton
    public TripDao provideTripDao(AppDatabase database) {
        return database.tripDao();
    }

    @Provides
    @Singleton
    public LocalSessionDataCleaner provideLocalSessionDataCleaner(
            AppDatabase appDatabase,
            @ApplicationContext Context context) {
        return () -> {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    appDatabase.clearAllTables();
                    context.getSharedPreferences("SYNC_PREF", Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();
                } catch (Exception exception) {
                    Timber.tag(TAG).e(exception,
                            "Failed to clear local session data");
                }
            });
            executor.shutdown();
        };
    }
}