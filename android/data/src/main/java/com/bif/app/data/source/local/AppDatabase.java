package com.bif.app.data.source.local;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;

@Database(entities = {
        FriendEntity.class,
        FavoriteEntity.class,
        GroupEntity.class,
        GroupFriendCrossRef.class,
        PlaceEntity.class,
        SyncQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class
}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract FriendDao friendDao();
    public abstract FavoriteDao favoriteDao();
    public abstract GroupDao groupDao();
    public abstract PlaceDao placeDao();
    public abstract SyncQueueDao syncQueueDao();
    public abstract SearchHistoryDao searchHistoryDao();
    public abstract ChatMessageDao chatMessageDao();

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `chat_messages` ("
                    + "`id` TEXT NOT NULL, "
                    + "`groupId` TEXT, "
                    + "`senderUserId` TEXT, "
                    + "`senderName` TEXT, "
                    + "`content` TEXT, "
                    + "`type` TEXT, "
                    + "`sentAt` INTEGER NOT NULL, "
                    + "`clientMessageId` TEXT, "
                    + "`sharedLatitude` REAL NOT NULL, "
                    + "`sharedLongitude` REAL NOT NULL, "
                    + "`sharedAddress` TEXT, "
                    + "`confirmed` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`id`))");
        }
    };
}

