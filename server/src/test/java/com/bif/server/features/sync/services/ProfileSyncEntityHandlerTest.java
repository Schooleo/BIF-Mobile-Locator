package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileSyncEntityHandlerTest {

    @Mock
    private UserRepository userRepository;

    private ProfileSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProfileSyncEntityHandler(userRepository);
    }

    @Test
    void entityType_ReturnsProfile() {
        assertEquals("profile", handler.entityType());
    }

    @Test
    void applyPushedChange_WhenUpdate_UsesAuthenticatedUserId() {
        SyncChange pushed = new SyncChange();
        pushed.setOperation("UPDATE");
        pushed.setEntityId("spoofed-id");
        pushed.setPayload("{\"userId\":\"another-user\","
                + "\"displayName\":\"Alice\","
                + "\"email\":\"alice@bif.local\","
                + "\"avatarLetter\":\"A\","
                + "\"avatarColor\":123}");

        User existing = new User();
        existing.setId("user-1");
        existing.setUsername("Old");
        existing.setEmail("old@bif.local");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        String resultPayload = handler.applyPushedChange(pushed, "user-1",
                10L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("user-1", saved.getId());
        assertEquals("Alice", saved.getUsername());
        assertEquals("alice@bif.local", saved.getEmail());
        assertEquals("A", saved.getAvatarLetter());
        assertEquals(123, saved.getAvatarColor());
        assertEquals(10L, saved.getServerVersion());
        assertEquals("user-1", saved.getLastModifiedBy());

        assertNotNull(resultPayload);
        assertTrue(resultPayload.contains("\"userId\":\"user-1\""));
        assertTrue(resultPayload.contains("\"serverVersion\":10"));
    }

    @Test
    void applyPushedChange_WhenDelete_MarksUserDeleted() {
        SyncChange pushed = new SyncChange();
        pushed.setOperation("DELETE");
        pushed.setEntityId("user-1");

        User existing = new User();
        existing.setId("user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(existing));

        String resultPayload = handler.applyPushedChange(pushed, "user-1",
                12L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertTrue(saved.isDeleted());
        assertEquals(12L, saved.getServerVersion());
        assertEquals("user-1", saved.getLastModifiedBy());
        assertNotNull(resultPayload);
        assertTrue(resultPayload.contains("\"deleted\":true"));
    }

    @Test
    void resolvePayload_WhenPayloadMissing_LoadsFromUserRepository() {
        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType("profile");
        entry.setEntityId("user-1");
        entry.setUserId("user-1");
        entry.setServerVersion(8L);

        User user = new User();
        user.setId("user-1");
        user.setUsername("Alice");
        user.setEmail("alice@bif.local");
        user.setAvatarLetter("A");
        user.setAvatarColor(777);
        user.setUpdatedAt(Instant.parse("2026-03-30T08:00:00Z"));

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        String payload = handler.resolvePayload(entry);

        assertNotNull(payload);
        assertTrue(payload.contains("\"userId\":\"user-1\""));
        assertTrue(payload.contains("\"displayName\":\"Alice\""));
        assertTrue(payload.contains("\"serverVersion\":8"));
    }
}