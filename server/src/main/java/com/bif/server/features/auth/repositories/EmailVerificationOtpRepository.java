package com.bif.server.features.auth.repositories;

import com.bif.server.features.auth.models.EmailVerificationOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailVerificationOtpRepository extends MongoRepository<EmailVerificationOtp, String> {
    Optional<EmailVerificationOtp> findByEmailIgnoreCase(String email);
}
