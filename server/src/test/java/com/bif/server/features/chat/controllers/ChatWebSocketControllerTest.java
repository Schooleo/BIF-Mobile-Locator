package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    private ChatWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatWebSocketController(chatService);
    }

    @Test
    void sendMessage_SetsGroupIdAndSaves() {
        ChatMessage input = new ChatMessage();
        input.setContent("Hello");
        input.setSenderUserId("u1");

        ChatMessage saved = new ChatMessage();
        saved.setId("m1");
        saved.setGroupId("g1");
        saved.setContent("Hello");
        when(chatService.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = controller.sendMessage("g1", input);

        ArgumentCaptor<ChatMessage> captor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertEquals("g1", captor.getValue().getGroupId());
        assertEquals("TEXT", captor.getValue().getType());
        assertSame(saved, result);
    }

    @Test
    void sendMessage_PreservesExistingType() {
        ChatMessage input = new ChatMessage();
        input.setType("SYSTEM");
        when(chatService.save(any(ChatMessage.class))).thenReturn(input);

        controller.sendMessage("g1", input);

        ArgumentCaptor<ChatMessage> captor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertEquals("SYSTEM", captor.getValue().getType());
    }

    @Test
    void acknowledgeMessage_WhenFound_ReturnsConfirmed() {
        ChatMessage confirmed = new ChatMessage();
        confirmed.setConfirmed(true);
        confirmed.setClientMessageId("client-1");
        when(chatService.confirmMessage("client-1"))
                .thenReturn(Optional.of(confirmed));

        ChatMessage ackPayload = new ChatMessage();
        ackPayload.setClientMessageId("client-1");

        ChatMessage result = controller.acknowledgeMessage("g1", ackPayload);

        assertNotNull(result);
        assertTrue(result.isConfirmed());
    }

    @Test
    void acknowledgeMessage_WhenMissing_ReturnsNull() {
        when(chatService.confirmMessage("client-x"))
                .thenReturn(Optional.empty());

        ChatMessage ackPayload = new ChatMessage();
        ackPayload.setClientMessageId("client-x");

        ChatMessage result = controller.acknowledgeMessage("g1", ackPayload);

        assertNull(result);
    }

    @Test
    void shareLocation_SetsTypeToLocation() {
        ChatMessage input = new ChatMessage();
        input.setSenderUserId("u1");
        input.setSharedAddress("123 Main St");

        ChatMessage saved = new ChatMessage();
        saved.setType("LOCATION");
        when(chatService.save(any(ChatMessage.class))).thenReturn(saved);

        ChatMessage result = controller.shareLocation("g1", input);

        ArgumentCaptor<ChatMessage> captor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertEquals("g1", captor.getValue().getGroupId());
        assertEquals("LOCATION", captor.getValue().getType());
        assertSame(saved, result);
    }
}
