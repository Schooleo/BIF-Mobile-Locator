package com.bif.server.features.user.repositories;

import com.bif.server.features.user.models.User;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, String> {
}