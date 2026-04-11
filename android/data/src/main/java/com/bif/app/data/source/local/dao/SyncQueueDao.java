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

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' "
            + "AND (userId = :userId OR userId IS NULL) "
            + "ORDER BY createdAt ASC, id ASC")
    List<SyncQueueEntity> getPendingForUser(String userId);

    @Insert
    void enqueue(SyncQueueEntity entry);

    @Update
    void update(SyncQueueEntity entry);

    @Query("DELETE FROM sync_queue WHERE id = :id")
    void remove(int id);

    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    void removeByEntity(String entityType, String entityId);

    @Query("SELECT DISTINCT entityId FROM sync_queue "
            + "WHERE (userId = :userId OR userId IS NULL) "
            + "AND entityType = :entityType "
            + "AND status IN ('PENDING', 'IN_FLIGHT', 'FAILED', 'BLOCKED')")
    List<String> getTrackedEntityIds(String userId, String entityType);

    @Query("UPDATE sync_queue SET status = 'PENDING' "
            + "WHERE status = 'IN_FLIGHT'")
    void resetInFlight();
}
