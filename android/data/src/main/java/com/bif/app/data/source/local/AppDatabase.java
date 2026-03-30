package com.bif.app.data.source.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.source.local.entity.SocialActionQueueEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.source.local.entity.TripPlanEntity;
import com.bif.app.data.source.local.entity.TripStopEntity;

@Database(entities = {
        FriendEntity.class,
        FriendshipEntity.class,
        FavoriteEntity.class,
        GroupEntity.class,
        GroupFriendCrossRef.class,
        PlaceEntity.class,
        SyncQueueEntity.class,
        SocialActionQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class,
        TripPlanEntity.class,
        TripStopEntity.class
    }, version = 14, exportSchema = false)
@TypeConverters({FriendshipStatusConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    public abstract FriendDao friendDao();
    public abstract FriendshipDao friendshipDao();
    public abstract FavoriteDao favoriteDao();
    public abstract GroupDao groupDao();
    public abstract PlaceDao placeDao();
    public abstract SyncQueueDao syncQueueDao();
    public abstract SocialActionQueueDao socialActionQueueDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract ChatMessageDao chatMessageDao();
    public abstract TripDao tripDao();
}

