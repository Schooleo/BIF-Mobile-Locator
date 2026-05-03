package com.bif.app.data.source.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bif.app.data.source.local.entity.SearchHistoryEntity;

import java.util.List;

@Dao
public interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 5")
    LiveData<List<SearchHistoryEntity>> getRecent();

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 5")
    List<SearchHistoryEntity> getRecentSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(SearchHistoryEntity entry);

    @Query("DELETE FROM search_history WHERE id NOT IN "
            + "(SELECT id FROM search_history "
            + "ORDER BY searchedAt DESC LIMIT 5)")
    void evictOldest();

    @Query("DELETE FROM search_history")
    void clear();
}
