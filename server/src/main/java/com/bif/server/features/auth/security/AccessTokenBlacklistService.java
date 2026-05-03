package com.bif.server.features.auth.security;

import com.bif.server.features.auth.models.RevokedAccessToken;
import com.bif.server.features.auth.repositories.RevokedAccessTokenRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AccessTokenBlacklistService {
    private final JwtService jwtService;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;

    public AccessTokenBlacklistService(
            JwtService jwtService,
            RevokedAccessTokenRepository revokedAccessTokenRepository
    ) {
        this.jwtService = jwtService;
        this.revokedAccessTokenRepository = revokedAccessTokenRepository;
    }

    public void revoke(String accessToken) {
        try {
            String jti = jwtService.extractJti(accessToken);
            Instant expiresAt = jwtService.extractExpiration(accessToken);

            if (jti == null || jti.isBlank() || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
                return;
            }

            RevokedAccessToken revokedAccessToken = new RevokedAccessToken();
            revokedAccessToken.setJti(jti);
            revokedAccessToken.setExpiresAt(expiresAt);
            revokedAccessTokenRepository.save(revokedAccessToken);
        } catch (DuplicateKeyException ignored) {
            // Idempotent revoke for repeated logout requests.
        } catch (RuntimeException ignored) {
            // Ignore malformed/expired tokens during logout to preserve idempotency.
        }
    }

    public boolean isRevoked(String accessToken) {
        try {
            String jti = jwtService.extractJti(accessToken);
            return jti != null && !jti.isBlank() && revokedAccessTokenRepository.existsByJti(jti);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
