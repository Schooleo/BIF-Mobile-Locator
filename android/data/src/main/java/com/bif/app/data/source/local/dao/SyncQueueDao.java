package com.bif.app.data.source.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.bif.app.data.source.local.entity.SyncQueueEntity;

import java.util.List;

@Dao
public interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' "
            + "ORDER BY createdAt ASC, id ASC")
    List<SyncQueueEntity> getPending();

    @Insert
    void enqueue(SyncQueueEntity entry);

    @Update
    void update(SyncQueueEntity entry);

    @Query("DELETE FROM sync_queue WHERE id = :id")
    void remove(int id);

    @Query("UPDATE sync_queue SET status = 'PENDING' "
            + "WHERE status = 'IN_FLIGHT'")
    void resetInFlight();
}
