package com.bif.server.features.place.repositories;

import com.bif.server.features.place.models.PlaceMapping;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

public class PlaceMappingRepositoryImpl implements PlaceMappingRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public PlaceMappingRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public PlaceMapping upsertByExternalKey(String externalSource,
                                            String externalId,
                                            String candidateInternalPlaceId,
                                            String name,
                                            double lat,
                                            double lng) {
        Query mappingQuery = Query.query(
                Criteria.where("externalSource").is(externalSource)
                        .and("externalId").is(externalId));

        Update mappingUpdate = new Update()
                .setOnInsert("internalPlaceId", candidateInternalPlaceId)
                .setOnInsert("externalSource", externalSource)
                .setOnInsert("externalId", externalId)
                .setOnInsert("createdAt", Instant.now())
                .set("name", name)
                .set("location", new GeoJsonPoint(lng, lat));

        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);

        try {
            return mongoTemplate.findAndModify(mappingQuery, mappingUpdate, options, PlaceMapping.class);
        } catch (DataIntegrityViolationException ex) {
            return null;
        }
    }
}
