package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.ChatMessage;
import java.util.List;

public interface IChatRepository {
    LiveData<List<ChatMessage>> getMessagesByGroup(String groupId);
    void sendMessage(ChatMessage message);
    void sendLocationMessage(String groupId, String senderUserId,
                             double latitude, double longitude, String address);
    LiveData<AiTripDraftResult> draftTripFromQuery(String query);
    void insertLocalMessage(ChatMessage message);
    void refreshMessages(String groupId);
    void connectToGroup(String groupId);
    void disconnectFromGroup();
}

