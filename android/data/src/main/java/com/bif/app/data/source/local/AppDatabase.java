package com.bif.app.data.source.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;

@Database(entities = {
        FriendEntity.class,
        FavoriteEntity.class,
        GroupEntity.class,
        GroupFriendCrossRef.class
}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FriendDao friendDao();
    public abstract FavoriteDao favoriteDao();
    public abstract GroupDao groupDao();
}
