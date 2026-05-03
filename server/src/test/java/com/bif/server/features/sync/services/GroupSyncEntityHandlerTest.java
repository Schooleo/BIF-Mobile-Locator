package com.bif.server.features.sync.services;

import com.bif.server.features.group.dto.AddMemberRequest;
import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.group.services.GroupService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupSyncEntityHandlerTest {

    @Mock
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

    private ObjectMapper objectMapper;
    private GroupSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
	objectMapper = new ObjectMapper();
	handler = new GroupSyncEntityHandler(groupService, groupRepository,
		objectMapper);
    }

    @Test
    void applyPushedChange_whenCreate_callsGroupServiceAndReturnsPayload()
	    throws Exception {
	Group created = new Group();
	created.setId("g-1");
	created.setName("Explorers");
	created.setOwnerId("user-1");
	created.setMemberIds(List.of("user-1", "user-2"));
	created.setMemberRoles(Map.of("user-1", "ADMIN", "user-2",
		"MEMBER"));
	created.setMemberCount(2);
	when(groupService.create(any(CreateGroupRequest.class)))
		.thenReturn(created);

	SyncChange pushed = new SyncChange();
	pushed.setOperation("CREATE");
	pushed.setPayload("{\"name\":\"Explorers\","
		+ "\"ownerId\":\"user-1\","
		+ "\"memberIds\":[\"user-2\"]}");

	String payload = handler.applyPushedChange(pushed, "user-1", 12L);

	ArgumentCaptor<CreateGroupRequest> requestCaptor =
		ArgumentCaptor.forClass(CreateGroupRequest.class);
	verify(groupService).create(requestCaptor.capture());
	assertEquals("Explorers", requestCaptor.getValue().getName());
	assertEquals("user-1", requestCaptor.getValue().getOwnerId());

	JsonNode response = objectMapper.readTree(payload);
	assertEquals("g-1", response.get("id").asText());
	assertEquals(12L, response.get("serverVersion").asLong());
    }

    @Test
    void applyPushedChange_whenAddMember_updatesGroup() throws Exception {
	Group updated = new Group();
	updated.setId("g-1");
	updated.setMemberIds(List.of("user-1", "user-2"));
	updated.setMemberRoles(Map.of("user-1", "ADMIN", "user-2",
		"MEMBER"));
	updated.setMemberCount(2);
	when(groupService.addMember(eq("g-1"), eq("user-1"),
		any(AddMemberRequest.class))).thenReturn(Optional.of(updated));

	SyncChange pushed = new SyncChange();
	pushed.setEntityId("g-1");
	pushed.setOperation("ADD_MEMBER");
	pushed.setPayload("{\"memberId\":\"user-2\","
		+ "\"role\":\"member\"}");

	String payload = handler.applyPushedChange(pushed, "user-1", 13L);

	ArgumentCaptor<AddMemberRequest> requestCaptor =
		ArgumentCaptor.forClass(AddMemberRequest.class);
	verify(groupService).addMember(eq("g-1"), eq("user-1"),
		requestCaptor.capture());
	assertEquals("user-2", requestCaptor.getValue().getMemberId());
	assertEquals("member", requestCaptor.getValue().getRole());

	JsonNode response = objectMapper.readTree(payload);
	assertEquals("g-1", response.get("id").asText());
	assertEquals(13L, response.get("serverVersion").asLong());
    }

    @Test
    void applyPushedChange_whenDelete_returnsTombstonePayload()
	    throws Exception {
	SyncChange pushed = new SyncChange();
	pushed.setEntityId("g-1");
	pushed.setOperation("DELETE");

	String payload = handler.applyPushedChange(pushed, "owner-1", 21L);

	verify(groupService).deleteById("g-1", "owner-1");
	JsonNode response = objectMapper.readTree(payload);
	assertEquals("g-1", response.get("id").asText());
	assertEquals(true, response.get("deleted").asBoolean());
	assertEquals(21L, response.get("serverVersion").asLong());
    }

    @Test
    void resolvePayload_readsGroupFromRepository() {
	Group group = new Group();
	group.setId("g-1");
	group.setName("Hikers");
	group.setServerVersion(4L);
	when(groupRepository.findById("g-1")).thenReturn(Optional.of(group));

	SyncChangeEntry entry = new SyncChangeEntry();
	entry.setEntityId("g-1");
	entry.setServerVersion(9L);

	String resolved = handler.resolvePayload(entry);

	assertNotNull(resolved);
	verify(groupRepository).findById("g-1");
    }
}
