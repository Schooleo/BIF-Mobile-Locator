package com.bif.app.data.source.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.bif.app.data.source.local.entity.ChatMessageEntity;
import java.util.List;

@Dao
public interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE groupId = :groupId ORDER BY sentAt ASC")
    LiveData<List<ChatMessageEntity>> getByGroupId(String groupId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChatMessageEntity> messages);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChatMessageEntity message);

        @Query("DELETE FROM chat_messages WHERE id IN ("
            + "SELECT id FROM chat_messages WHERE groupId = :groupId "
            + "ORDER BY sentAt DESC, id DESC LIMIT -1 OFFSET :keepCount)")
        void pruneGroupToLimit(String groupId, int keepCount);

    @Query("DELETE FROM chat_messages WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM chat_messages WHERE groupId = :groupId")
    void deleteByGroupId(String groupId);
}
