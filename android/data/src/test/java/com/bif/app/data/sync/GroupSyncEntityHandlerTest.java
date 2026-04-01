package com.bif.app.data.sync;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

public class GroupSyncEntityHandlerTest {

    @Mock
    private GroupDao mockGroupDao;

    private GroupSyncEntityHandler handler;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        handler = new GroupSyncEntityHandler(mockGroupDao, new Gson());
    }

    @Test
    public void applyPulledChange_delete_deletesByServerId() {
        SyncChangeDto change = new SyncChangeDto();
        change.operation = "DELETE";
        change.entityId = "group-1";

        handler.applyPulledChange(change, "user-1");

        verify(mockGroupDao).deleteByServerId("group-1");
    }

    @Test
    public void applyPulledChange_upsert_insertsGroupEntity() {
        when(mockGroupDao.getAllGroupsSync()).thenReturn(Collections.emptyList());

        SyncChangeDto change = new SyncChangeDto();
        change.operation = "UPDATE";
        change.serverVersion = 22L;
        change.payload = "{\"id\":\"group-1\",\"name\":\"Explorers\","
                + "\"avatarLetter\":\"E\",\"avatarColor\":123," 
                + "\"ownerId\":\"user-1\","
                + "\"memberIds\":[\"user-1\",\"user-2\"],"
                + "\"memberRoles\":{\"user-1\":\"ADMIN\",\"user-2\":\"MEMBER\"}}";

        handler.applyPulledChange(change, "user-1");

        ArgumentCaptor<GroupEntity> captor =
                ArgumentCaptor.forClass(GroupEntity.class);
        verify(mockGroupDao).insertGroup(captor.capture());

        GroupEntity saved = captor.getValue();
        org.junit.Assert.assertEquals("group-1", saved.getServerId());
        org.junit.Assert.assertEquals("Explorers", saved.getName());
        org.junit.Assert.assertEquals(22L, saved.getServerVersion());
        org.junit.Assert.assertTrue(saved.isOwner());
    }
}

