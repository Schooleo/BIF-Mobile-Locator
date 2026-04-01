package com.bif.app.data.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bif.app.core.network.dto.sync.SyncChangeDto;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;

public class FavoriteSyncEntityHandlerTest {

    private FavoriteDao mockDao;
    private Gson gson;
    private FavoriteSyncEntityHandler handler;

    @Before
    public void setUp() {
        mockDao = mock(FavoriteDao.class);
        gson = new Gson();
        handler = new FavoriteSyncEntityHandler(mockDao, gson);
    }

    @Test
    public void applyPulledChange_ValidCreate_UpsertsToDao() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "favorite";
        change.entityId = "f1";
        change.operation = "CREATE";
        change.serverVersion = 10;
        change.payload = "{\"id\":\"f1\",\"name\":\"New Fav\"}";

        handler.applyPulledChange(change, "user1");

        verify(mockDao).upsert(any(FavoriteEntity.class));
    }

    @Test
    public void applyPulledChange_DeleteWithoutPayload_UpsertsTombstone() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "favorite";
        change.entityId = "f2";
        change.operation = "DELETE";
        change.serverVersion = 15;
        change.payload = null;

        handler.applyPulledChange(change, "user1");

        verify(mockDao).upsert(any(FavoriteEntity.class));
    }

    @Test
    public void applyPulledChange_MalformedPayload_DoesNotUpsert() {
        SyncChangeDto change = new SyncChangeDto();
        change.entityType = "favorite";
        change.payload = "{invalid-json}";

        handler.applyPulledChange(change, "user1");

        verify(mockDao, never()).upsert(any(FavoriteEntity.class));
    }
}

