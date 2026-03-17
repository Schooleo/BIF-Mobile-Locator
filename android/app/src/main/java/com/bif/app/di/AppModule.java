package com.bif.app.di;

import android.content.Context;

import androidx.room.Room;

import com.bif.app.data.source.local.AppDatabase;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.GroupDao;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

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
                .fallbackToDestructiveMigration()
                .build();
    }

    @Provides
    @Singleton
    public FavoriteDao provideFavoriteDao(AppDatabase database) {
        return database.favoriteDao();
    }

    @Provides
    @Singleton
    public FriendDao provideFriendDao(AppDatabase database) { return database.friendDao(); }

    @Provides
    @Singleton
    public GroupDao provideGroupDao(AppDatabase database) { return database.groupDao(); }
}
