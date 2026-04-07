package com.bif.app.data.source.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.PlaceEntity;

import java.util.List;

@Dao
public interface PlaceDao {
    @Query("SELECT * FROM places WHERE ownerUserId = :ownerUserId "
            + "AND deleted = 0 ORDER BY name ASC")
    LiveData<List<PlaceEntity>> getAll(String ownerUserId);

    @Query("SELECT * FROM places WHERE (name LIKE '%' || :q || '%' "
            + "OR address LIKE '%' || :q || '%') AND ownerUserId = :ownerUserId "
            + "AND deleted = 0")
    LiveData<List<PlaceEntity>> search(String q, String ownerUserId);

    @Query("SELECT * FROM places WHERE (name LIKE '%' || :q || '%' "
            + "OR address LIKE '%' || :q || '%') AND ownerUserId = :ownerUserId "
            + "AND deleted = 0")
    List<PlaceEntity> searchByName(String q, String ownerUserId);

    @Query("SELECT * FROM places WHERE id = :id AND ownerUserId = :ownerUserId LIMIT 1")
    PlaceEntity getByIdSync(String id, String ownerUserId);

    @Query("SELECT * FROM places WHERE ABS(latitude - :lat) < 0.0001 AND ABS(longitude - :lng) < 0.0001 "
            + "AND ownerUserId = :ownerUserId AND deleted = 0 LIMIT 1")
    PlaceEntity getByLocationSync(double lat, double lng, String ownerUserId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PlaceEntity place);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PlaceEntity> places);

    @Query("SELECT MAX(serverVersion) FROM places WHERE ownerUserId = :ownerUserId")
    long getMaxServerVersion(String ownerUserId);

    @Query("SELECT COUNT(*) FROM places WHERE ownerUserId = :ownerUserId "
            + "AND deleted = 0")
    int count(String ownerUserId);

    @Query("DELETE FROM places WHERE id IN "
            + "(SELECT id FROM places WHERE ownerUserId = :ownerUserId "
            + "AND deleted = 0 "
            + "ORDER BY lastSyncedAt ASC LIMIT :excess)")
    void evictOldest(int excess, String ownerUserId);
}
