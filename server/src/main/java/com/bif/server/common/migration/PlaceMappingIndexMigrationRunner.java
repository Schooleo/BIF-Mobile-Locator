package com.bif.server.common.migration;

import com.bif.server.common.migration.model.SchemaMigration;
import com.bif.server.common.migration.repository.SchemaMigrationRepository;
import com.bif.server.features.place.models.PlaceMapping;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(1)
@Profile("!test")
public class PlaceMappingIndexMigrationRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceMappingIndexMigrationRunner.class);
    private static final String MIGRATION_ID = "place-mapping-index-v2";
    private static final String LEGACY_INDEX_NAME = "uk_source_extid";

    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationRepository schemaMigrationRepository;

    @Value("${app.migration.place-mapping-index.enabled:true}")
    private boolean migrationEnabled;

    public PlaceMappingIndexMigrationRunner(MongoTemplate mongoTemplate,
                                            SchemaMigrationRepository schemaMigrationRepository) {
        this.mongoTemplate = mongoTemplate;
        this.schemaMigrationRepository = schemaMigrationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) {
            LOGGER.info("PlaceMapping index migration is disabled by config.");
            return;
        }
        if (schemaMigrationRepository.existsById(MIGRATION_ID)) {
            LOGGER.info("PlaceMapping index migration already applied. Skipping.");
            return;
        }

        verifyNoDuplicateResolvableSeeds();

        long invalidSeedCount = countInvalidIdentitySeeds();
        if (invalidSeedCount > 0) {
            LOGGER.warn("Found {} PlaceMapping rows with invalid/blank identity seed; they will be excluded by partial unique index.",
                    invalidSeedCount);
        }

        mongoTemplate.indexOps(PlaceMapping.class).ensureIndex(
                new Index()
                        .on("externalSource", Sort.Direction.ASC)
                        .on("externalId", Sort.Direction.ASC)
                        .unique()
                        .named("uk_source_extid_v2")
                        .partial(PartialIndexFilter.of(
                                Criteria.where("externalSource").gt("")
                                        .and("externalId").gt(""))));

        dropLegacyIndexIfExists();

        SchemaMigration marker = new SchemaMigration();
        marker.setId(MIGRATION_ID);
        marker.setExecutedAt(Instant.now());
        schemaMigrationRepository.save(marker);

        LOGGER.info("PlaceMapping index migration complete. invalidSeedCount={}", invalidSeedCount);
    }

    private void verifyNoDuplicateResolvableSeeds() {
        MongoCollection<Document> collection = mongoTemplate.getCollection("place_mappings");
        List<Document> pipeline = List.of(
                new Document("$match", new Document("externalSource", new Document("$exists", true)
                        .append("$type", "string")
                        .append("$ne", ""))
                        .append("externalId", new Document("$exists", true)
                                .append("$type", "string")
                                .append("$ne", ""))),
                new Document("$group", new Document("_id", new Document("externalSource", "$externalSource")
                        .append("externalId", "$externalId"))
                        .append("count", new Document("$sum", 1))
                        .append("ids", new Document("$push", "$_id"))),
                new Document("$match", new Document("count", new Document("$gt", 1))),
                new Document("$limit", 1)
        );

        List<Document> duplicates = collection.aggregate(pipeline).into(new ArrayList<>());
        if (!duplicates.isEmpty()) {
            Document duplicate = duplicates.get(0);
            throw new IllegalStateException("Cannot migrate PlaceMapping index: duplicate resolvable identity seed detected: "
                    + duplicate.toJson());
        }
    }

    private long countInvalidIdentitySeeds() {
        MongoCollection<Document> collection = mongoTemplate.getCollection("place_mappings");
        Document invalidSource = new Document("$or", List.of(
                new Document("externalSource", new Document("$exists", false)),
                new Document("externalSource", null),
                new Document("externalSource", ""),
                new Document("externalSource", new Document("$not", new Document("$type", "string")))
        ));
        Document invalidExternalId = new Document("$or", List.of(
                new Document("externalId", new Document("$exists", false)),
                new Document("externalId", null),
                new Document("externalId", ""),
                new Document("externalId", new Document("$not", new Document("$type", "string")))
        ));
        return collection.countDocuments(new Document("$or", List.of(invalidSource, invalidExternalId)));
    }

    private void dropLegacyIndexIfExists() {
        List<IndexInfo> indexes = mongoTemplate.indexOps(PlaceMapping.class).getIndexInfo();
        for (IndexInfo indexInfo : indexes) {
            if (LEGACY_INDEX_NAME.equals(indexInfo.getName())) {
                mongoTemplate.indexOps(PlaceMapping.class).dropIndex(LEGACY_INDEX_NAME);
                LOGGER.info("Dropped legacy PlaceMapping index {}.", LEGACY_INDEX_NAME);
                break;
            }
        }
    }
}
