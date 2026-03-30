package com.bif.app.data.sync;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.bif.app.core.network.dto.SyncChangeDto;
import com.bif.app.data.source.local.ProfileDao;
import com.bif.app.data.source.local.entity.ProfileEntity;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class ProfileSyncEntityHandlerTest {

    private ProfileDao profileDao;
    private ProfileSyncEntityHandler handler;

    @Before
    public void setUp() {
        profileDao = mock(ProfileDao.class);
        Context appContext = mock(Context.class);
        handler = new ProfileSyncEntityHandler(profileDao, new Gson(),
                appContext);
    }

    @Test
    public void applyPulledChange_validPayload_upsertsProfile() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "profile";
        change.entityId = "user-1";
        change.operation = "UPDATE";
        change.serverVersion = 8;
        change.payload = "{\"userId\":\"user-1\","
                + "\"displayName\":\"Alice\","
                + "\"email\":\"alice@bif.com\","
                + "\"avatarLetter\":\"A\","
                + "\"avatarColor\":123}";

        handler.applyPulledChange(change, "different-user");

        ArgumentCaptor<ProfileEntity> captor = ArgumentCaptor
                .forClass(ProfileEntity.class);
        verify(profileDao).upsert(captor.capture());

        ProfileEntity saved = captor.getValue();
        assertEquals("user-1", saved.userId);
        assertEquals("Alice", saved.displayName);
        assertEquals("alice@bif.com", saved.email);
        assertEquals(8, saved.serverVersion);
    }

    @Test
    public void applyPulledChange_deleteWithoutPayload_upsertsTombstone() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "profile";
        change.entityId = "user-2";
        change.operation = "DELETE";
        change.serverVersion = 11;
        change.payload = null;

        handler.applyPulledChange(change, "user-2");

        ArgumentCaptor<ProfileEntity> captor = ArgumentCaptor
                .forClass(ProfileEntity.class);
        verify(profileDao).upsert(captor.capture());

        ProfileEntity saved = captor.getValue();
        assertEquals("user-2", saved.userId);
        assertEquals(11, saved.serverVersion);
        assertEquals(true, saved.deleted);
    }

    @Test
    public void applyPulledChange_malformedPayload_doesNotUpsert() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "profile";
        change.operation = "UPDATE";
        change.payload = "{invalid-json}";

        handler.applyPulledChange(change, "user-1");

        verify(profileDao, never()).upsert(any(ProfileEntity.class));
    }
}
