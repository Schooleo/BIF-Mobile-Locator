package com.bif.server.features.chat.controllers;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRestControllerTest {

    @Mock
    private ChatService chatService;

    private ChatRestController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatRestController(chatService);
    }

    @Test
    void getMessages_ReturnsData() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getAll()).thenReturn(List.of(msg));

        List<ChatMessage> result = controller.getMessages();

        assertEquals(1, result.size());
    }

    @Test
    void getMessageById_WhenFound_ReturnsOk() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getById("c1")).thenReturn(Optional.of(msg));

        ResponseEntity<ChatMessage> result = controller.getMessageById("c1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(msg, result.getBody());
    }

    @Test
    void getMessageById_WhenMissing_ReturnsNotFound() {
        when(chatService.getById("c1")).thenReturn(Optional.empty());

        ResponseEntity<ChatMessage> result = controller.getMessageById("c1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getMessagesByGroup_ReturnsData() {
        ChatMessage msg = new ChatMessage();
        when(chatService.getByGroupId("g1")).thenReturn(List.of(msg));

        List<ChatMessage> result = controller.getMessagesByGroup("g1");

        assertEquals(1, result.size());
    }

    @Test
    void getMessagesByGroupPaged_ReturnsPage() {
        ChatMessage msg = new ChatMessage();
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(chatService.getByGroupIdPaginated("g1", 0, 20)).thenReturn(page);

        Page<ChatMessage> result = controller.getMessagesByGroupPaged("g1", 0, 20);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void upsertMessage_DelegatesToService() {
        ChatMessage input = new ChatMessage();
        when(chatService.save(input)).thenReturn(input);

        ChatMessage result = controller.upsertMessage(input);

        assertSame(input, result);
    }

    @Test
    void confirmMessage_WhenFound_ReturnsOk() {
        ChatMessage msg = new ChatMessage();
        msg.setConfirmed(true);
        when(chatService.confirmMessage("client-1"))
                .thenReturn(Optional.of(msg));

        ResponseEntity<ChatMessage> result = controller.confirmMessage("client-1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isConfirmed());
    }

    @Test
    void confirmMessage_WhenMissing_ReturnsNotFound() {
        when(chatService.confirmMessage("client-x"))
                .thenReturn(Optional.empty());

        ResponseEntity<ChatMessage> result = controller.confirmMessage("client-x");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void deleteMessage_WhenDeleted_ReturnsNoContent() {
        when(chatService.deleteById("c1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteMessage("c1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteMessage_WhenMissing_ReturnsNotFound() {
        when(chatService.deleteById("c1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteMessage("c1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
