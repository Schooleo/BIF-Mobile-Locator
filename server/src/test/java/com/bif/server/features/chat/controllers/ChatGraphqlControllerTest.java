package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatGraphqlControllerTest {

    @Mock
    private ChatService chatService;

    private ChatGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatGraphqlController(chatService);
    }

    @Test
    void chatMessages_ReturnsData() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getAll()).thenReturn(List.of(msg));

        List<ChatMessage> result = controller.chatMessages();

        assertEquals(1, result.size());
    }

    @Test
    void chatMessage_WhenFound_ReturnsEntity() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getById("c1")).thenReturn(Optional.of(msg));

        ChatMessage result = controller.chatMessage("c1");

        assertSame(msg, result);
    }

    @Test
    void chatMessage_WhenMissing_ReturnsNull() {
        when(chatService.getById("c1")).thenReturn(Optional.empty());

        assertNull(controller.chatMessage("c1"));
    }

    @Test
    void chatMessagesByGroup_ReturnsData() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getByGroupId("g1")).thenReturn(List.of(msg));

        List<ChatMessage> result = controller.chatMessagesByGroup("g1");

        assertEquals(1, result.size());
    }

    @Test
    void upsertChatMessage_DelegatesToService() {
        ChatMessage input = new ChatMessage();
        when(chatService.save(input)).thenReturn(input);

        ChatMessage result = controller.upsertChatMessage(input);

        assertSame(input, result);
    }

    @Test
    void deleteChatMessage_DelegatesToService() {
        when(chatService.deleteById("c1")).thenReturn(true);

        assertTrue(controller.deleteChatMessage("c1"));
    }
}
