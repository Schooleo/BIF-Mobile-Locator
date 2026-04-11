package com.bif.app.data.source.local.dao;

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
    @Query("SELECT * FROM favorites WHERE userId = :userId AND deleted = 0 ORDER BY id DESC")
    LiveData<List<FavoriteEntity>> getAll(String userId);

    @Query("SELECT * FROM favorites WHERE userId = :userId AND deleted = 0 "
            + "AND (name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%')")
    LiveData<List<FavoriteEntity>> searchFavorites(String userId, String query);

    @Query("SELECT * FROM favorites WHERE userId = :userId")
    List<FavoriteEntity> getAllSync(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(FavoriteEntity favorite);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(FavoriteEntity favorite);

    @Query("SELECT * FROM favorites WHERE id = :id AND userId = :userId LIMIT 1")
    FavoriteEntity findById(String id, String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<FavoriteEntity> favorites);

    @Delete
    void delete(FavoriteEntity favorite);

    @Update
    void update(FavoriteEntity favorite);

    @Update
    void updateAll(List<FavoriteEntity> favorites);

    @Query("DELETE FROM favorites WHERE id = :id AND userId = :userId")
    void deleteById(String id, String userId);

    @Query("DELETE FROM favorites WHERE userId = :userId")
    void deleteAll(String userId);

    @Transaction
    default void replaceAll(String userId, List<FavoriteEntity> favorites) {
        deleteAll(userId);
        if (favorites != null && !favorites.isEmpty()) {
            insertAll(favorites);
        }
    }
}