package com.bif.app.data.source.local.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import com.bif.app.data.source.local.converter.FriendshipStatusConverter;
import com.bif.app.data.source.local.converter.UploadStatusConverter;
import com.bif.app.data.source.local.dao.ChatMessageDao;
import com.bif.app.data.source.local.dao.FavoriteDao;
import com.bif.app.data.source.local.dao.FriendDao;
import com.bif.app.data.source.local.dao.FriendshipDao;
import com.bif.app.data.source.local.dao.GroupDao;
import com.bif.app.data.source.local.dao.PlaceDao;
import com.bif.app.data.source.local.dao.ProfileDao;
import com.bif.app.data.source.local.dao.ReviewDao;
import com.bif.app.data.source.local.dao.SearchHistoryDao;
import com.bif.app.data.source.local.dao.SyncQueueDao;
import com.bif.app.data.source.local.dao.TripDao;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.ReviewEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
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
        ProfileEntity.class,
        ReviewEntity.class,
        SyncQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class,
        TripPlanEntity.class,
        TripStopEntity.class
}, version = 15, exportSchema = false)
@TypeConverters({ FriendshipStatusConverter.class, UploadStatusConverter.class })
public abstract class AppDatabase extends RoomDatabase {
    public abstract FriendDao friendDao();

    public abstract FriendshipDao friendshipDao();

    public abstract FavoriteDao favoriteDao();

    public abstract GroupDao groupDao();

    public abstract PlaceDao placeDao();

    public abstract ProfileDao profileDao();

    public abstract ReviewDao reviewDao();

    public abstract SyncQueueDao syncQueueDao();

    public abstract SearchHistoryDao searchHistoryDao();

    public abstract ChatMessageDao chatMessageDao();

    public abstract TripDao tripDao();
}