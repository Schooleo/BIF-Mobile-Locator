package com.bif.locator.data.source.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.bif.locator.domain.model.Favorite;

@Database(entities = {Favorite.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
}
