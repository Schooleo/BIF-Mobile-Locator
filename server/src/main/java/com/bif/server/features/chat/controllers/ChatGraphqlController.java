package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class ChatGraphqlController {
    private final ChatService chatService;

    public ChatGraphqlController(ChatService chatService) {
        this.chatService = chatService;
    }

    @QueryMapping
    public List<ChatMessage> chatMessages() {
        return chatService.getAll();
    }

    @QueryMapping
    public ChatMessage chatMessage(@Argument String id) {
        return chatService.getById(id).orElse(null);
    }

    @QueryMapping
    public List<ChatMessage> chatMessagesByGroup(@Argument String groupId) {
        return chatService.getByGroupId(groupId);
    }

    @MutationMapping
    public ChatMessage upsertChatMessage(@Argument ChatMessage input) {
        return chatService.save(input);
    }

    @MutationMapping
    public Boolean deleteChatMessage(@Argument String id) {
        return chatService.deleteById(id);
    }
}
