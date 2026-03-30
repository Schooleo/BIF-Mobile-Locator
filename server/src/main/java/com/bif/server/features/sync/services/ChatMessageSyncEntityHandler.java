package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.chat.services.ChatService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

@Component
public class ChatMessageSyncEntityHandler implements SyncEntityHandler {

	private final ChatService chatService;
	private final ChatMessageRepository chatMessageRepository;
	private final ObjectMapper objectMapper;

	public ChatMessageSyncEntityHandler(ChatService chatService,
										ChatMessageRepository chatMessageRepository,
										ObjectMapper objectMapper) {
		this.chatService = chatService;
		this.chatMessageRepository = chatMessageRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public String entityType() {
		return "chatMessage";
	}

	@Override
	public String applyPushedChange(SyncChange pushed, String userId,
									long newVersion) {
		ChatMessagePayload payload = parsePayload(pushed.getPayload());
		String operation = pushed.getOperation() != null
				? pushed.getOperation().toUpperCase(Locale.ROOT)
				: "UPSERT";

		if ("DELETE".equals(operation)) {
			String targetId = pushed.getEntityId();
			if ((targetId == null || targetId.isBlank()) && payload != null) {
				targetId = payload.id;
			}
			if (targetId == null || targetId.isBlank()) {
				return pushed.getPayload();
			}

			chatService.deleteById(targetId);
			ChatMessagePayload tombstone = new ChatMessagePayload();
			tombstone.id = targetId;
			tombstone.deleted = true;
			tombstone.serverVersion = newVersion;
			return writePayload(tombstone);
		}

		if (payload == null) {
			return pushed.getPayload();
		}

		ChatMessage message = findExisting(payload).orElseGet(ChatMessage::new);
		if (message.getId() == null || message.getId().isBlank()) {
			message.setId(payload.id);
		}
		message.setGroupId(payload.groupId);
		message.setSenderUserId(payload.senderUserId != null
				? payload.senderUserId : userId);
		message.setContent(payload.content);
		message.setSentAt(payload.sentAt);
		message.setClientMessageId(payload.clientMessageId);
		message.setType(payload.type);
		message.setSharedLocation(payload.sharedLocation);
		message.setSharedAddress(payload.sharedAddress);
		message.setConfirmed(payload.confirmed);
		message.setDeleted(payload.deleted);
		message.setServerVersion(newVersion);
		message.setLastModifiedBy(userId);

		ChatMessage saved = chatService.save(message);
		ChatMessagePayload responsePayload = toPayload(saved);
		responsePayload.serverVersion = newVersion;
		return writePayload(responsePayload);
	}

	@Override
	public String resolvePayload(SyncChangeEntry entry) {
		Optional<ChatMessage> messageOpt = chatMessageRepository
				.findById(entry.getEntityId());
		if (messageOpt.isEmpty()) {
			return entry.getPayload();
		}

		ChatMessagePayload payload = toPayload(messageOpt.get());
		payload.serverVersion = Math.max(payload.serverVersion,
				entry.getServerVersion());
		return writePayload(payload);
	}

	private Optional<ChatMessage> findExisting(ChatMessagePayload payload) {
		if (payload.clientMessageId != null
				&& !payload.clientMessageId.isBlank()) {
			Optional<ChatMessage> byClientId = chatMessageRepository
					.findByClientMessageId(payload.clientMessageId);
			if (byClientId.isPresent()) {
				return byClientId;
			}
		}
		if (payload.id != null && !payload.id.isBlank()) {
			return chatMessageRepository.findById(payload.id);
		}
		return Optional.empty();
	}

	private ChatMessagePayload parsePayload(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(json, ChatMessagePayload.class);
		} catch (Exception e) {
			return null;
		}
	}

	private String writePayload(ChatMessagePayload payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (Exception e) {
			return null;
		}
	}

	private ChatMessagePayload toPayload(ChatMessage message) {
		ChatMessagePayload payload = new ChatMessagePayload();
		payload.id = message.getId();
		payload.groupId = message.getGroupId();
		payload.senderUserId = message.getSenderUserId();
		payload.content = message.getContent();
		payload.sentAt = message.getSentAt();
		payload.clientMessageId = message.getClientMessageId();
		payload.type = message.getType();
		payload.sharedLocation = message.getSharedLocation();
		payload.sharedAddress = message.getSharedAddress();
		payload.confirmed = message.isConfirmed();
		payload.serverVersion = message.getServerVersion();
		payload.deleted = message.isDeleted();
		return payload;
	}

	private static class ChatMessagePayload {
		public String id;
		public String groupId;
		public String senderUserId;
		public String content;
		public Instant sentAt;
		public String clientMessageId;
		public String type;
		public Location sharedLocation;
		public String sharedAddress;
		public boolean confirmed;
		public long serverVersion;
		public boolean deleted;
	}
}
