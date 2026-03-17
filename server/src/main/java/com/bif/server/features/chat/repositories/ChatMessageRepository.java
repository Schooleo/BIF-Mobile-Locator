package com.bif.server.features.chat.repositories;

import com.bif.server.features.chat.models.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByGroupIdOrderBySentAtAsc(String groupId);
}