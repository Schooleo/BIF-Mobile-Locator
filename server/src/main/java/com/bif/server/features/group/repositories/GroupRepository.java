package com.bif.server.features.group.repositories;

import com.bif.server.features.group.models.Group;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface GroupRepository extends MongoRepository<Group, String> {
	List<Group> findByOwnerIdOrMemberIdsContaining(String ownerId, String memberId);
}