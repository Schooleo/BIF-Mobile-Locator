package com.bif.server.features.chat.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatService {
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    public List<ChatMessage> getAll() {
        return chatMessageRepository.findAll();
    }

    public List<ChatMessage> getByGroupId(String groupId) {
        return chatMessageRepository.findByGroupIdOrderBySentAtAsc(groupId);
    }

    public Optional<ChatMessage> getById(String id) {
        return chatMessageRepository.findById(id);
    }

    public ChatMessage save(ChatMessage message) {
        return chatMessageRepository.save(message);
    }

    public boolean deleteById(String id) {
        if (!chatMessageRepository.existsById(id)) {
            return false;
        }
        chatMessageRepository.deleteById(id);
        return true;
    }
}
