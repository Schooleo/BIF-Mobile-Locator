package com.bif.app.data.sync;

import static org.mockito.Mockito.verify;

import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FriendshipSyncEntityHandlerTest {

    @Mock
    private FriendshipDao mockFriendshipDao;

    @Mock
    private FriendDao mockFriendDao;

    private FriendshipSyncEntityHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new FriendshipSyncEntityHandler(mockFriendshipDao,
                mockFriendDao, new Gson());
    }

    @Test
    public void applyPulledChange_delete_deletesByServerId() {
        SyncChangeDto change = new SyncChangeDto();
        change.operation = "DELETE";
        change.entityId = "friendship-1";

        handler.applyPulledChange(change, "user-1");

        verify(mockFriendshipDao).deleteByServerId("friendship-1");
    }

    @Test
    public void applyPulledChange_accepted_upsertsFriendshipAndFriend() {
        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.payload = "{\"id\":\"friendship-1\","
                + "\"requesterId\":\"user-1\","
                + "\"receiverId\":\"user-2\","
                + "\"status\":\"ACCEPTED\","
                + "\"createdAt\":\"2026-03-30T10:00:00Z\","
                + "\"updatedAt\":\"2026-03-30T10:01:00Z\"}";

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<FriendshipEntity> friendshipCaptor =
                ArgumentCaptor.forClass(FriendshipEntity.class);
        verify(mockFriendshipDao).insert(friendshipCaptor.capture());
        org.junit.Assert.assertEquals(FriendshipStatus.ACCEPTED,
                friendshipCaptor.getValue().status);

        ArgumentCaptor<FriendEntity> friendCaptor =
                ArgumentCaptor.forClass(FriendEntity.class);
        verify(mockFriendDao).insert(friendCaptor.capture());
        org.junit.Assert.assertEquals("user-2",
                friendCaptor.getValue().serverUserId);
    }

    @Test
    public void applyPulledChange_rejected_removesFriendCounterpart() {
        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.payload = "{\"id\":\"friendship-1\","
                + "\"requesterId\":\"user-1\","
                + "\"receiverId\":\"user-2\","
                + "\"status\":\"REJECTED\"}";

        handler.applyPulledChange(change, "user-1");

        verify(mockFriendDao).deleteByServerUserId("user-2");
    }
}
