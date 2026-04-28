package com.bif.server.common.migration;

import com.bif.server.common.migration.model.SchemaMigration;
import com.bif.server.common.migration.repository.SchemaMigrationRepository;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteContractV2MigrationRunnerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PlaceIdentityService placeIdentityService;

    @Mock
    private SchemaMigrationRepository schemaMigrationRepository;

    private FavoriteContractV2MigrationRunner runner;

    @BeforeEach
    void setUp() {
        runner = new FavoriteContractV2MigrationRunner(
                mongoTemplate,
                placeIdentityService,
                schemaMigrationRepository);
        ReflectionTestUtils.setField(runner, "migrationEnabled", true);
    }

    @Test
    void run_WhenMarkerExists_SkipsMigration() throws Exception {
        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(true);

        runner.run(null);

        verify(mongoTemplate, never()).findAll(eq(Document.class), anyString());
        verify(schemaMigrationRepository, never()).save(any(SchemaMigration.class));
    }

    @Test
    void run_WhenValidFavorite_ResolvesAndPersistsCanonicalPlaceId() throws Exception {
        Document favorite = new Document("_id", "f1")
            .append("userId", "u1")
            .append("externalSource", "GOOGLE_MAPS")
            .append("externalId", "gm-1")
            .append("placeName", "Coffee")
            .append("name", "Coffee")
            .append("location", new Document("latitude", 10.0).append("longitude", 20.0))
            .append("placeId", "legacy-id");

        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(false);
        when(mongoTemplate.findAll(Document.class, "favorites")).thenReturn(List.of(favorite));
        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-1", 10.0, 20.0, "Coffee"))
                .thenReturn("canonical-1");
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq("favorites")))
            .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        runner.run(null);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), updateCaptor.capture(), eq("favorites"));
        String updateJson = updateCaptor.getValue().getUpdateObject().toJson();
        assertTrue(updateJson.contains("\"placeId\""));
        assertTrue(updateJson.contains("canonical-1"));
        assertTrue(updateJson.contains("\"$unset\""));
        assertTrue(updateJson.contains("\"externalId\""));
        assertTrue(updateJson.contains("\"placeName\""));

        ArgumentCaptor<SchemaMigration> migrationCaptor = ArgumentCaptor.forClass(SchemaMigration.class);
        verify(schemaMigrationRepository).save(migrationCaptor.capture());
        assertEquals("favorite-contract-v2", migrationCaptor.getValue().getId());
    }

    @Test
    void run_WhenFavoriteSeedInvalid_SkipsFavoriteAndStillWritesMarker() throws Exception {
        Document invalid = new Document("_id", "f-bad")
            .append("userId", "u1")
            .append("externalSource", "GOOGLE_MAPS")
            .append("externalId", "gm-bad")
            .append("placeName", "No location");

        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(false);
        when(mongoTemplate.findAll(Document.class, "favorites")).thenReturn(List.of(invalid));

        runner.run(null);

        verify(placeIdentityService, never()).resolveInternalPlaceId(anyString(), anyString(), anyDouble(), anyDouble(), anyString());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq("favorites"));
        verify(schemaMigrationRepository).save(any(SchemaMigration.class));
    }

    @Test
    void run_WhenMigrationHasFailures_DoesNotWriteMarker() throws Exception {
        Document failed = new Document("_id", "f-fail")
                .append("userId", "u1")
                .append("externalSource", "GOOGLE_MAPS")
                .append("externalId", "gm-fail")
                .append("placeName", "Coffee")
                .append("location", new Document("latitude", 10.0).append("longitude", 20.0));

        when(schemaMigrationRepository.existsById("favorite-contract-v2")).thenReturn(false);
        when(mongoTemplate.findAll(Document.class, "favorites")).thenReturn(List.of(failed));
        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "gm-fail", 10.0, 20.0, "Coffee"))
                .thenReturn(" ");

        runner.run(null);

        verify(schemaMigrationRepository, never()).save(any(SchemaMigration.class));
    }
}
