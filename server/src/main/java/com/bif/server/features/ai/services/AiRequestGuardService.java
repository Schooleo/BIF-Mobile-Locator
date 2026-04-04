package com.bif.server.features.ai.services;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.bif.server.features.ai.config.AiGuardProperties;
import com.bif.server.features.ai.dto.graphql.AiFailureCode;

@Service
public class AiRequestGuardService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiRequestGuardService.class);

    private final AiGuardProperties aiGuardProperties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> requestHistoryByUser = new ConcurrentHashMap<>();

    @Autowired
    public AiRequestGuardService(AiGuardProperties aiGuardProperties) {
        this(aiGuardProperties, Clock.systemUTC());
    }

    AiRequestGuardService(AiGuardProperties aiGuardProperties, Clock clock) {
        this.aiGuardProperties = aiGuardProperties;
        this.clock = clock;
    }

    public AiRequestDecision evaluateCurrentRequest() {
        return evaluate(SecurityContextHolder.getContext().getAuthentication());
    }

    AiRequestDecision evaluate(Authentication authentication) {
        String userId = extractUserId(authentication);
        if (aiGuardProperties.isAuthRequired() && userId == null) {
            LOGGER.info("Denied unauthenticated AI request");
            return AiRequestDecision.denied(
                    AiFailureCode.UNAUTHORIZED,
                    "Authentication is required for AI mutations.");
        }

        if (userId == null) {
            return AiRequestDecision.allowed(null);
        }

        if (isRateLimited(userId)) {
            LOGGER.warn("Rate-limited AI request for user {}", userId);
            return AiRequestDecision.denied(
                    AiFailureCode.RATE_LIMITED,
                    "AI request rate limit exceeded. Please try again later.");
        }

        return AiRequestDecision.allowed(userId);
    }

    private String extractUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null) {
            return null;
        }

        String userId = principal.toString().trim();
        return userId.isBlank() || "anonymousUser".equalsIgnoreCase(userId)
                ? null
                : userId;
    }

    private boolean isRateLimited(String userId) {
        if (aiGuardProperties.getMaxRequestsPerWindow() <= 0
                || aiGuardProperties.getRateLimitWindowSeconds() <= 0) {
            return false;
        }

        Instant now = Instant.now(clock);
        Instant threshold = now.minusSeconds(aiGuardProperties.getRateLimitWindowSeconds());
        Deque<Instant> history = requestHistoryByUser.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst().isBefore(threshold)) {
                history.removeFirst();
            }
            if (history.size() >= aiGuardProperties.getMaxRequestsPerWindow()) {
                return true;
            }
            history.addLast(now);
            return false;
        }
    }
}
