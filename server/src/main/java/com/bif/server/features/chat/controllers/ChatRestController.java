package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
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

    @PostMapping
    public ChatMessage upsertMessage(@RequestBody ChatMessage message) {
        return chatService.save(message);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String id) {
        return chatService.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
