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
        ReviewEntity.class,
        SyncQueueEntity.class,
        SearchHistoryEntity.class,
        ChatMessageEntity.class,
        TripPlanEntity.class,
        TripMemberCrossRef.class,
        TripStopEntity.class
    }, version = 24, exportSchema = false)
@TypeConverters({ FriendshipStatusConverter.class, UploadStatusConverter.class })
public abstract class AppDatabase extends RoomDatabase {
    private static boolean hasColumn(SupportSQLiteDatabase database, String tableName, String columnName) {
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

    public static final Migration MIGRATION_19_20 = new Migration(19, 20) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "sync_queue", "userId")) {
                database.execSQL("ALTER TABLE sync_queue ADD COLUMN userId TEXT");
            }

            if (!hasIndex(database, "index_sync_queue_userId_entityType_status")) {
                database.execSQL("CREATE INDEX index_sync_queue_userId_entityType_status "
                        + "ON sync_queue(userId, entityType, status)");
            }
        }

        private boolean hasIndex(SupportSQLiteDatabase database, String indexName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='index' AND name=?", new Object[]{indexName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
    };

    public static final Migration MIGRATION_18_19 = new Migration(18, 19) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // v18 existed in two branches with different schema contents.
            // Ensure reviews columns expected by current entity exist for both paths.
            if (!hasColumn(database, "reviews", "externalSource")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN externalSource TEXT");
            }
            if (!hasColumn(database, "reviews", "externalId")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN externalId TEXT");
            }
            if (!hasColumn(database, "reviews", "lat")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN lat REAL");
            }
            if (!hasColumn(database, "reviews", "lng")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN lng REAL");
            }
            if (!hasColumn(database, "reviews", "placeName")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN placeName TEXT");
            }

            if (!hasColumn(database, "favorites", "pendingSync")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0");
            } else {
                database.execSQL("UPDATE favorites SET pendingSync = 0 WHERE pendingSync IS NULL");
            }

            if (!hasIndex(database, "index_reviews_createdAt")) {
                database.execSQL("CREATE INDEX index_reviews_createdAt ON reviews(createdAt)");
            }

            if (!hasIndex(database, "index_favorites_userId_deleted")) {
                database.execSQL("CREATE INDEX index_favorites_userId_deleted ON favorites(userId, deleted)");
            }
        }

        private boolean hasIndex(SupportSQLiteDatabase database, String indexName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='index' AND name=?", new Object[]{indexName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
    };

    public static final Migration MIGRATION_15_16 = new Migration(15, 16) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasTable(database, "trip_members")) {
                database.execSQL("CREATE TABLE trip_members ("
                        + "tripId TEXT NOT NULL, "
                        + "userId TEXT NOT NULL, "
                        + "PRIMARY KEY(tripId, userId), "
                        + "FOREIGN KEY(tripId) REFERENCES trip_plans(id) ON DELETE CASCADE"
                        + ")");
                database.execSQL("CREATE INDEX index_trip_members_tripId ON trip_members(tripId)");
                database.execSQL("CREATE INDEX index_trip_members_userId ON trip_members(userId)");
            }

            if (!hasColumn(database, "trip_stops", "address")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN address TEXT");
            }
        }

        private boolean hasTable(SupportSQLiteDatabase database, String tableName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='table' AND name=?", new Object[]{tableName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }

    };

    public static final Migration MIGRATION_16_17 = new Migration(16, 17) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasTable(database, "reviews")) {
                database.execSQL("CREATE TABLE reviews ("
                        + "placeId TEXT NOT NULL, "
                        + "userId TEXT NOT NULL, "
                        + "userName TEXT, "
                        + "stars INTEGER NOT NULL, "
                        + "comment TEXT, "
                        + "createdAt INTEGER NOT NULL, "
                        + "serverVersion INTEGER NOT NULL, "
                        + "deleted INTEGER NOT NULL, "
                        + "lastSyncedAt INTEGER NOT NULL, "
                        + "pendingSync INTEGER NOT NULL, "
                        + "PRIMARY KEY(placeId, userId)"
                        + ")");
            }

            if (!hasIndex(database, "index_reviews_createdAt")) {
                database.execSQL("CREATE INDEX index_reviews_createdAt ON reviews(createdAt)");
            }

            if (!hasColumn(database, "trip_members", "role")) {
                database.execSQL("ALTER TABLE trip_members ADD COLUMN role TEXT NOT NULL DEFAULT ''");
            }
        }

        private boolean hasTable(SupportSQLiteDatabase database, String tableName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='table' AND name=?", new Object[]{tableName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }

        private boolean hasIndex(SupportSQLiteDatabase database, String indexName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='index' AND name=?", new Object[]{indexName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }

    };

    public static final Migration MIGRATION_17_18 = new Migration(17, 18) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "reviews", "externalSource")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN externalSource TEXT");
            }
            if (!hasColumn(database, "reviews", "externalId")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN externalId TEXT");
            }
            if (!hasColumn(database, "reviews", "lat")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN lat REAL");
            }
            if (!hasColumn(database, "reviews", "lng")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN lng REAL");
            }
            if (!hasColumn(database, "reviews", "placeName")) {
                database.execSQL("ALTER TABLE reviews ADD COLUMN placeName TEXT");
            }
        }

    };

    public static final Migration MIGRATION_20_21 = new Migration(20, 21) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Recreate sync_queue table to make userId nullable (was NOT NULL DEFAULT '')
            // SQLite doesn't support modifying column constraints, so we must recreate
            database.execSQL("CREATE TABLE sync_queue_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "userId TEXT, "
                    + "clientChangeId TEXT, "
                    + "entityType TEXT, "
                    + "entityId TEXT, "
                    + "operation TEXT, "
                    + "payload TEXT, "
                    + "retryCount INTEGER NOT NULL, "
                    + "createdAt INTEGER NOT NULL, "
                    + "status TEXT)");

            // Copy data from old table to new table
            database.execSQL("INSERT INTO sync_queue_new SELECT "
                    + "id, NULLIF(userId, ''), clientChangeId, entityType, entityId, operation, payload, "
                    + "retryCount, createdAt, status FROM sync_queue");

            // Drop old table
            database.execSQL("DROP TABLE sync_queue");

            // Rename new table
            database.execSQL("ALTER TABLE sync_queue_new RENAME TO sync_queue");

            // Recreate indices
            if (!hasIndex(database, "index_sync_queue_userId_entityType_status")) {
                database.execSQL("CREATE INDEX index_sync_queue_userId_entityType_status "
                        + "ON sync_queue(userId, entityType, status)");
            }

            // Ensure favorites.pendingSync has default value of 0
            if (!hasColumn(database, "favorites", "pendingSync")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN pendingSync INTEGER NOT NULL DEFAULT 0");
            } else {
                // Ensure existing null values are set to 0
                database.execSQL("UPDATE favorites SET pendingSync = 0 WHERE pendingSync IS NULL");
            }
        }

        private boolean hasIndex(SupportSQLiteDatabase database, String indexName) {
            Cursor cursor = database.query("SELECT name FROM sqlite_master "
                    + "WHERE type='index' AND name=?", new Object[]{indexName});
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
    };

    public static final Migration MIGRATION_21_22 = new Migration(21, 22) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "favorites", "placeId")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN placeId TEXT");
            }
            if (!hasColumn(database, "trip_stops", "addedByUserId")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN addedByUserId TEXT");
            }
            if (!hasColumn(database, "trip_stops", "addedByName")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN addedByName TEXT");
            }
            if (!hasColumn(database, "trip_stops", "addedByAvatarLetter")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN addedByAvatarLetter TEXT");
            }
            if (!hasColumn(database, "trip_stops", "addedByAvatarColor")) {
                database.execSQL("ALTER TABLE trip_stops ADD COLUMN addedByAvatarColor INTEGER NOT NULL DEFAULT 0");
            }

            if (!hasColumn(database, "trip_plans", "coverImageUrl")) {
                database.execSQL("ALTER TABLE trip_plans ADD COLUMN coverImageUrl TEXT");
            }
            if (!hasColumn(database, "trip_plans", "localCoverImagePath")) {
                database.execSQL("ALTER TABLE trip_plans ADD COLUMN localCoverImagePath TEXT");
            }
            if (!hasColumn(database, "trip_plans", "coverUploadStatus")) {
                database.execSQL("ALTER TABLE trip_plans ADD COLUMN coverUploadStatus TEXT NOT NULL DEFAULT 'SYNCED'");
            }
        }

    };

    public static final Migration MIGRATION_22_23 = new Migration(22, 23) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "favorites", "externalSource")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN externalSource TEXT");
            }
            if (!hasColumn(database, "favorites", "externalId")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN externalId TEXT");
            }
            if (!hasColumn(database, "favorites", "placeName")) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN placeName TEXT");
            }

            // Backfill deterministic identity seed for legacy favorites to avoid
            // sync rejections caused by missing canonical identity metadata.
            database.execSQL("UPDATE favorites SET externalSource = 'OSM' "
                    + "WHERE externalSource IS NULL OR TRIM(externalSource) = ''");
            database.execSQL("UPDATE favorites SET placeName = CASE "
                    + "WHEN placeName IS NULL OR TRIM(placeName) = '' THEN "
                    + "CASE "
                    + "WHEN name IS NOT NULL AND TRIM(name) <> '' THEN TRIM(name) "
                    + "WHEN address IS NOT NULL AND TRIM(address) <> '' THEN TRIM(address) "
                    + "ELSE placeName END "
                    + "ELSE placeName END");
            database.execSQL("UPDATE favorites SET externalId = CASE "
                    + "WHEN externalId IS NULL OR TRIM(externalId) = '' THEN "
                    + "CASE "
                    + "WHEN placeId IS NOT NULL AND TRIM(placeId) <> '' THEN TRIM(placeId) "
                    + "WHEN id IS NOT NULL AND TRIM(id) <> '' THEN TRIM(id) "
                    + "ELSE externalId END "
                    + "ELSE externalId END");
        }
    };

    public static final Migration MIGRATION_23_24 = new Migration(23, 24) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            if (!hasColumn(database, "places", "viewedAt")) {
                database.execSQL("ALTER TABLE places ADD COLUMN viewedAt INTEGER NOT NULL DEFAULT 0");
            }
        }
    };

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