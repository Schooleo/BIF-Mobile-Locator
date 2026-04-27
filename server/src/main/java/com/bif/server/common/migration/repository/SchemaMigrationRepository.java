package com.bif.server.common.migration.repository;

import com.bif.server.common.migration.model.SchemaMigration;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SchemaMigrationRepository extends MongoRepository<SchemaMigration, String> {
}
