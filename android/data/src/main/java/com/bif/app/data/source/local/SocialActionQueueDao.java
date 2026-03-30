package com.bif.app.data.source.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.bif.app.data.source.local.entity.SocialActionQueueEntity;

import java.util.List;

@Dao
public interface SocialActionQueueDao {
    @Query("SELECT * FROM social_action_queue WHERE status = 'PENDING' "
            + "AND scope = :scope AND userId = :userId "
            + "ORDER BY createdAt ASC")
    List<SocialActionQueueEntity> getPendingByScope(String scope,
                                                    String userId);

    @Query("SELECT COUNT(*) > 0 FROM social_action_queue "
            + "WHERE scope = :scope AND userId = :userId "
            + "AND status IN ('PENDING', 'IN_FLIGHT')")
    boolean hasUnresolvedByScope(String scope, String userId);

    @Insert
    void enqueue(SocialActionQueueEntity entry);

    @Update
    void update(SocialActionQueueEntity entry);

    @Query("DELETE FROM social_action_queue WHERE id = :id")
    void remove(int id);

    @Query("UPDATE social_action_queue SET status = 'PENDING' "
            + "WHERE status = 'IN_FLIGHT' AND userId = :userId")
    void resetInFlight(String userId);
}
