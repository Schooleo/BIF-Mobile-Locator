package com.bif.app.data.source.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;

@Database(entities = {
        FriendEntity.class,
        FavoriteEntity.class,
        GroupEntity.class,
        GroupFriendCrossRef.class,
        PlaceEntity.class,
        ProfileEntity.class,
        SyncQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class
    }, version = 8, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FriendDao friendDao();
    public abstract FavoriteDao favoriteDao();
    public abstract GroupDao groupDao();
    public abstract PlaceDao placeDao();
    public abstract ProfileDao profileDao();
    public abstract SyncQueueDao syncQueueDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract ChatMessageDao chatMessageDao();
}

