package com.bif.server.features.sync.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.chat.services.ChatService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageSyncEntityHandlerTest {

	@Mock
	private ChatService chatService;

	@Mock
	private ChatMessageRepository chatMessageRepository;

	private ObjectMapper objectMapper;
	private ChatMessageSyncEntityHandler handler;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		handler = new ChatMessageSyncEntityHandler(chatService,
				chatMessageRepository, objectMapper);
	}

	@Test
	void applyPushedChange_whenUpsert_savesMessageAndReturnsPayload()
			throws Exception {
		ChatMessage saved = new ChatMessage();
		saved.setId("m-1");
		saved.setGroupId("g-1");
		saved.setSenderUserId("u-1");
		saved.setContent("hello");
		saved.setSentAt(Instant.parse("2026-03-30T10:15:30Z"));
		saved.setServerVersion(8L);
		when(chatService.save(any(ChatMessage.class))).thenReturn(saved);

		SyncChange pushed = new SyncChange();
		pushed.setEntityId("m-1");
		pushed.setOperation("UPSERT");
		pushed.setPayload("{\"id\":\"m-1\",\"groupId\":\"g-1\","
				+ "\"senderUserId\":\"u-1\","
				+ "\"content\":\"hello\","
				+ "\"sentAt\":\"2026-03-30T10:15:30Z\"}");

		String payload = handler.applyPushedChange(pushed, "u-1", 8L);

		ArgumentCaptor<ChatMessage> captor =
				ArgumentCaptor.forClass(ChatMessage.class);
		verify(chatService).save(captor.capture());
		assertEquals("m-1", captor.getValue().getId());
		assertEquals(8L, captor.getValue().getServerVersion());
		assertEquals("u-1", captor.getValue().getLastModifiedBy());

		JsonNode response = objectMapper.readTree(payload);
		assertEquals("m-1", response.get("id").asText());
		assertEquals(8L, response.get("serverVersion").asLong());
	}

	@Test
	void applyPushedChange_whenDelete_returnsTombstonePayload()
			throws Exception {
		SyncChange pushed = new SyncChange();
		pushed.setEntityId("m-1");
		pushed.setOperation("DELETE");

		String payload = handler.applyPushedChange(pushed, "u-1", 9L);

		verify(chatService).deleteById("m-1");
		JsonNode response = objectMapper.readTree(payload);
		assertEquals("m-1", response.get("id").asText());
		assertEquals(true, response.get("deleted").asBoolean());
		assertEquals(9L, response.get("serverVersion").asLong());
	}

	@Test
	void resolvePayload_readsMessageFromRepository() {
		ChatMessage existing = new ChatMessage();
		existing.setId("m-1");
		existing.setContent("stored");
		existing.setServerVersion(4L);
		when(chatMessageRepository.findById("m-1"))
				.thenReturn(Optional.of(existing));

		SyncChangeEntry entry = new SyncChangeEntry();
		entry.setEntityId("m-1");
		entry.setServerVersion(7L);

		String resolved = handler.resolvePayload(entry);

		assertNotNull(resolved);
		verify(chatMessageRepository).findById("m-1");
	}
}
