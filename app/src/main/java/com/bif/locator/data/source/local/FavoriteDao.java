package com.bif.locator.data.source.local;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import com.bif.locator.domain.model.Favorite;
import java.util.List;
import androidx.lifecycle.LiveData;

@Dao
public interface FavoriteDao {
    @Query("SELECT * FROM favorites")
    LiveData<List<Favorite>> getAll();

    @Insert
    void insert(Favorite favorite);

    @Delete
    void delete(Favorite favorite);
}
