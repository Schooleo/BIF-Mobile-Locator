package com.bif.server.features.chat.repositories;

import com.bif.server.features.chat.models.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByGroupIdOrderBySentAtAsc(String groupId);

    Page<ChatMessage> findByGroupIdOrderBySentAtDesc(String groupId, Pageable pageable);

    Optional<ChatMessage> findByClientMessageId(String clientMessageId);
}