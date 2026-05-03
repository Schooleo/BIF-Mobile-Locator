package com.bif.server.features.auth.repositories;

import com.bif.server.features.auth.models.RevokedAccessToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RevokedAccessTokenRepository extends MongoRepository<RevokedAccessToken, String> {
    boolean existsByJti(String jti);
}
