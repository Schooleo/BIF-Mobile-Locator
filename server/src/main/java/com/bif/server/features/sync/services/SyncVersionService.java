package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncMetadata;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class SyncVersionService {
    private final MongoTemplate mongoTemplate;

    public SyncVersionService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long nextVersion() {
        Update update = new Update().inc("currentVersion", 1);
        FindAndModifyOptions opts = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);
        SyncMetadata meta = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is("global")),
                update, opts, SyncMetadata.class);
        return meta != null ? meta.getCurrentVersion() : 1;
    }

    public long getCurrentVersion() {
        SyncMetadata meta = mongoTemplate.findById("global", SyncMetadata.class);
        return meta == null ? 0 : meta.getCurrentVersion();
    }
}
