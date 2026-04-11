package com.bif.server.features.chat.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
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

    public Page<ChatMessage> getByGroupIdPaginated(String groupId, int page, int size) {
        return chatMessageRepository.findByGroupIdOrderBySentAtDesc(
                groupId, PageRequest.of(page, size));
    }

    public Optional<ChatMessage> getById(String id) {
        return chatMessageRepository.findById(id);
    }

    public ChatMessage save(ChatMessage message) {
        if (message.getSentAt() == null) {
            message.setSentAt(Instant.now());
        }
        if (message.getType() == null) {
            message.setType("TEXT");
        }
        if (!message.isDeleted()) {
            message.setConfirmed(true);
        }
        return chatMessageRepository.save(message);
    }

    public Optional<ChatMessage> confirmMessage(String clientMessageId) {
        Optional<ChatMessage> optMsg =
                chatMessageRepository.findByClientMessageId(clientMessageId);
        optMsg.ifPresent(msg -> {
            msg.setConfirmed(true);
            chatMessageRepository.save(msg);
        });
        return optMsg;
    }

    public boolean deleteById(String id) {
        if (!chatMessageRepository.existsById(id)) {
            return false;
        }
        chatMessageRepository.deleteById(id);
        return true;
    }
}
