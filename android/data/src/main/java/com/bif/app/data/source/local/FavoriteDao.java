package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import com.bif.app.data.source.local.entity.FavoriteEntity;

import java.util.List;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY id DESC")
    LiveData<List<FavoriteEntity>> getAll();

    @Query("SELECT * FROM favorites WHERE name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%'")
    LiveData<List<FavoriteEntity>> searchFavorites(String query);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteEntity favorite);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FavoriteEntity> favorites);

    @Delete
    void delete(FavoriteEntity favorite);

    @Update
    void update(FavoriteEntity favorite);

    @Update
    void updateAll(List<FavoriteEntity> favorites);

    @Query("DELETE FROM favorites")
    void deleteAll();

    @Transaction
    default void replaceAll(List<FavoriteEntity> favorites) {
        deleteAll();
        if (favorites != null && !favorites.isEmpty()) {
            insertAll(favorites);
        }
    }
}