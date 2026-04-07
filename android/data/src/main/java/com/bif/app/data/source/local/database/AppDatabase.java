package com.bif.app.data.source.local.database;

import android.database.Cursor;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.bif.app.data.source.local.converter.FriendshipStatusConverter;
import com.bif.app.data.source.local.converter.UploadStatusConverter;
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
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.bif.app.data.source.local.entity.SearchHistoryEntity;
import com.bif.app.data.source.local.entity.SyncQueueEntity;
import com.bif.app.data.source.local.entity.TripMemberCrossRef;
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
        SyncQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class,
        TripPlanEntity.class,
        TripMemberCrossRef.class,
        TripStopEntity.class
    }, version = 16, exportSchema = false)
@TypeConverters({ FriendshipStatusConverter.class, UploadStatusConverter.class })
public abstract class AppDatabase extends RoomDatabase {
    public static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "trip_stops", "address")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN address TEXT");
            }
        }

        private boolean hasColumn(SupportSQLiteDatabase database, String tableName, String columnName) {
            Cursor cursor = database.query("PRAGMA table_info(`" + tableName + "`)");
            try {
                int nameIndex = cursor.getColumnIndex("name");
                if (nameIndex < 0) {
                    nameIndex = 1;
                }
                while (cursor.moveToNext()) {
                    String existing = cursor.getString(nameIndex);
                    if (columnName.equals(existing)) {
                        return true;
                    }
                }
                return false;
            } finally {
                cursor.close();
            }
        }
    };

    public abstract FriendDao friendDao();

    public abstract FriendshipDao friendshipDao();

    public abstract FavoriteDao favoriteDao();

    public abstract GroupDao groupDao();

    public abstract PlaceDao placeDao();

    public abstract ProfileDao profileDao();

    public abstract SyncQueueDao syncQueueDao();

    public abstract SearchHistoryDao searchHistoryDao();

    public abstract ChatMessageDao chatMessageDao();

    public abstract TripDao tripDao();
}