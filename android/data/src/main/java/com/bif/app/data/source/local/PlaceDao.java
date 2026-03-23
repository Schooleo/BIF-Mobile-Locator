package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.PlaceEntity;

import java.util.List;

@Dao
public interface PlaceDao {
    @Query("SELECT * FROM places WHERE deleted = 0 ORDER BY name ASC")
    LiveData<List<PlaceEntity>> getAll();

    @Query("SELECT * FROM places WHERE (name LIKE '%' || :q || '%' "
            + "OR address LIKE '%' || :q || '%') AND deleted = 0")
    LiveData<List<PlaceEntity>> search(String q);

    @Query("SELECT * FROM places WHERE (name LIKE '%' || :q || '%' "
            + "OR address LIKE '%' || :q || '%') AND deleted = 0")
    List<PlaceEntity> searchByName(String q);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PlaceEntity place);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<PlaceEntity> places);

    @Query("SELECT MAX(serverVersion) FROM places")
    long getMaxServerVersion();

    @Query("SELECT COUNT(*) FROM places WHERE deleted = 0")
    int count();

    @Query("DELETE FROM places WHERE id IN "
            + "(SELECT id FROM places WHERE deleted = 0 "
            + "ORDER BY lastSyncedAt ASC LIMIT :excess)")
    void evictOldest(int excess);
}
