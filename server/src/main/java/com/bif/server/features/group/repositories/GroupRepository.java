package com.bif.server.features.group.repositories;

import com.bif.server.features.group.models.Group;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GroupRepository extends MongoRepository<Group, String> {
}