package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {
    private final ChatService chatService;

    public ChatWebSocketController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.send/{groupId}")
    @SendTo("/topic/chat/{groupId}")
    public ChatMessage sendMessage(
            @DestinationVariable String groupId,
            ChatMessage message) {
        message.setGroupId(groupId);
        if (message.getType() == null) {
            message.setType("TEXT");
        }
        return chatService.save(message);
    }

    @MessageMapping("/chat.ack/{groupId}")
    @SendTo("/topic/chat/{groupId}")
    public ChatMessage acknowledgeMessage(
            @DestinationVariable String groupId,
            ChatMessage ackPayload) {
        return chatService.confirmMessage(ackPayload.getClientMessageId())
                .orElse(null);
    }

    @MessageMapping("/chat.location/{groupId}")
    @SendTo("/topic/chat/{groupId}")
    public ChatMessage shareLocation(
            @DestinationVariable String groupId,
            ChatMessage message) {
        message.setGroupId(groupId);
        message.setType("LOCATION");
        return chatService.save(message);
    }
}
