package com.bif.app.di;

import android.content.Context;

import androidx.room.Room;

import com.bif.app.core.auth.LocalSessionDataCleaner;
import com.bif.app.data.source.local.AppDatabase;
import com.bif.app.data.source.local.ChatMessageDao;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.PlaceDao;
import com.bif.app.data.source.local.SearchHistoryDao;
import com.bif.app.data.source.local.SyncQueueDao;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import javax.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public ExecutorService provideExecutorService() {
        return Executors.newFixedThreadPool(4);
    }

    @Provides
    @Singleton
    public FusedLocationProviderClient provideLocationClient(@ApplicationContext Context context) {
        return LocationServices.getFusedLocationProviderClient(context);
    }

    @Provides
    @Singleton
    public AppDatabase provideAppDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(
                        context,
                        AppDatabase.class,
                        "bif_database"
                )
                .fallbackToDestructiveMigration(true)
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
    public LocalSessionDataCleaner provideLocalSessionDataCleaner(AppDatabase appDatabase) {
      return () -> {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
          try {
            appDatabase.clearAllTables();
          } catch (Exception ignored) {
            // Keep logout flow resilient even if local cleanup fails.
          }
        });
        executor.shutdown();
      };
    }
}
