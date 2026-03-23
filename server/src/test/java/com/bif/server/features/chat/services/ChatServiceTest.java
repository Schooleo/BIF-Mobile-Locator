package com.bif.server.features.chat.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatMessageRepository);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        ChatMessage msg = new ChatMessage();
        when(chatMessageRepository.findAll()).thenReturn(List.of(msg));

        List<ChatMessage> result = chatService.getAll();

        assertEquals(1, result.size());
        verify(chatMessageRepository).findAll();
    }

    @Test
    void getByGroupId_ReturnsRepositoryData() {
        ChatMessage msg = new ChatMessage();
        when(chatMessageRepository.findByGroupIdOrderBySentAtAsc("g1")).thenReturn(List.of(msg));

        List<ChatMessage> result = chatService.getByGroupId("g1");

        assertEquals(1, result.size());
        verify(chatMessageRepository).findByGroupIdOrderBySentAtAsc("g1");
    }

    @Test
    void getByGroupIdPaginated_ReturnsPage() {
        ChatMessage msg = new ChatMessage();
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(chatMessageRepository.findByGroupIdOrderBySentAtDesc(
                eq("g1"), any(PageRequest.class))).thenReturn(page);

        Page<ChatMessage> result = chatService.getByGroupIdPaginated("g1", 0, 20);

        assertEquals(1, result.getTotalElements());
        verify(chatMessageRepository).findByGroupIdOrderBySentAtDesc(
                eq("g1"), any(PageRequest.class));
    }

    @Test
    void getById_ReturnsOptional() {
        ChatMessage msg = new ChatMessage();
        when(chatMessageRepository.findById("c1")).thenReturn(Optional.of(msg));

        Optional<ChatMessage> result = chatService.getById("c1");

        assertTrue(result.isPresent());
        verify(chatMessageRepository).findById("c1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        ChatMessage msg = new ChatMessage();
        msg.setSentAt(Instant.now());
        when(chatMessageRepository.save(msg)).thenReturn(msg);

        ChatMessage result = chatService.save(msg);

        assertSame(msg, result);
        verify(chatMessageRepository).save(msg);
    }

    @Test
    void save_AutoSetsSentAtWhenNull() {
        ChatMessage msg = new ChatMessage();
        msg.setSentAt(null);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatMessage result = chatService.save(msg);

        assertNotNull(result.getSentAt());
    }

    @Test
    void save_AutoSetsTypeWhenNull() {
        ChatMessage msg = new ChatMessage();
        msg.setType(null);
        msg.setSentAt(Instant.now());
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        ChatMessage result = chatService.save(msg);

        assertEquals("TEXT", result.getType());
    }

    @Test
    void confirmMessage_WhenFound_SetsConfirmedTrue() {
        ChatMessage msg = new ChatMessage();
        msg.setClientMessageId("client-1");
        msg.setConfirmed(false);
        when(chatMessageRepository.findByClientMessageId("client-1"))
                .thenReturn(Optional.of(msg));
        when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(i -> i.getArgument(0));

        Optional<ChatMessage> result = chatService.confirmMessage("client-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().isConfirmed());
        verify(chatMessageRepository).save(msg);
    }

    @Test
    void confirmMessage_WhenMissing_ReturnsEmpty() {
        when(chatMessageRepository.findByClientMessageId("client-x"))
                .thenReturn(Optional.empty());

        Optional<ChatMessage> result = chatService.confirmMessage("client-x");

        assertTrue(result.isEmpty());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(chatMessageRepository.existsById("c1")).thenReturn(true);

        boolean result = chatService.deleteById("c1");

        assertTrue(result);
        verify(chatMessageRepository).deleteById("c1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(chatMessageRepository.existsById("c1")).thenReturn(false);

        boolean result = chatService.deleteById("c1");

        assertFalse(result);
        verify(chatMessageRepository, never()).deleteById(anyString());
    }
}
