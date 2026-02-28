package com.bif.app.data.source.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.bif.app.domain.model.Favorite;

@Database(entities = {Favorite.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FavoriteDao favoriteDao();
}
