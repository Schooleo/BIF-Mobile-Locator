package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncVersionServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private SyncVersionService syncVersionService;

    @BeforeEach
    void setUp() {
        syncVersionService = new SyncVersionService(mongoTemplate);
    }

    @Test
    void nextVersion_ReturnsIncrementedVersion() {
        SyncMetadata meta = new SyncMetadata();
        meta.setCurrentVersion(42);
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                eq(SyncMetadata.class)))
                .thenReturn(meta);

        long version = syncVersionService.nextVersion();

        assertEquals(42, version);
    }

    @Test
    void nextVersion_WhenNullReturned_ReturnsOne() {
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class),
                eq(SyncMetadata.class)))
                .thenReturn(null);

        long version = syncVersionService.nextVersion();

        assertEquals(1, version);
    }

    @Test
    void getCurrentVersion_WhenMetadataExists_ReturnsVersion() {
        SyncMetadata meta = new SyncMetadata();
        meta.setCurrentVersion(10);
        when(mongoTemplate.findById("global", SyncMetadata.class))
                .thenReturn(meta);

        long version = syncVersionService.getCurrentVersion();

        assertEquals(10, version);
    }

    @Test
    void getCurrentVersion_WhenNoMetadata_ReturnsZero() {
        when(mongoTemplate.findById("global", SyncMetadata.class))
                .thenReturn(null);

        long version = syncVersionService.getCurrentVersion();

        assertEquals(0, version);
    }
}
