package com.bif.server.features.sync.repositories;

import com.bif.server.features.sync.models.SyncChangeEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SyncChangeRepository extends MongoRepository<SyncChangeEntry, String> {
    List<SyncChangeEntry> findByServerVersionGreaterThanOrderByServerVersionAsc(
            long version);

    Optional<SyncChangeEntry> findByClientChangeId(String clientChangeId);
}
