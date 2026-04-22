package com.bif.server.features.auth.repositories;

import com.bif.server.features.auth.models.PasswordResetOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetOtpRepository extends MongoRepository<PasswordResetOtp, String> {
}