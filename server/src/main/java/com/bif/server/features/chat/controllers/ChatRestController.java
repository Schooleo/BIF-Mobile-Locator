package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {
    private final ChatService chatService;

    public ChatRestController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatMessage> getMessages() {
        return chatService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatMessage> getMessageById(@PathVariable String id) {
        return chatService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/group/{groupId}")
    public List<ChatMessage> getMessagesByGroup(@PathVariable String groupId) {
        return chatService.getByGroupId(groupId);
    }

    @GetMapping("/group/{groupId}/paged")
    public Page<ChatMessage> getMessagesByGroupPaged(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chatService.getByGroupIdPaginated(groupId, page, size);
    }

    @PostMapping
    public ChatMessage upsertMessage(@RequestBody ChatMessage message) {
        return chatService.save(message);
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<ChatMessage> confirmMessage(@PathVariable String id) {
        return chatService.confirmMessage(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        return chatService.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}

