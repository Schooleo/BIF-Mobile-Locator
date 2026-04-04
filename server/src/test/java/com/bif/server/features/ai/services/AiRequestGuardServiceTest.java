package com.bif.server.features.ai.services;

import com.bif.server.features.ai.config.AiGuardProperties;
import com.bif.server.features.ai.dto.graphql.AiFailureCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRequestGuardServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void evaluateCurrentRequest_DeniesAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key",
                        "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                )
        );

        AiRequestGuardService service = new AiRequestGuardService(
                guardProperties(),
                fixedClock()
        );

        AiRequestDecision decision = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );

        assertEquals(AiFailureCode.UNAUTHORIZED, decision.failureCode());
        assertTrue(decision.message().contains("Authentication is required"));
    }

    @Test
    void evaluateCurrentRequest_AllowsAuthenticatedCallerUntilLimitReached() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_USER")
        );

        AiRequestGuardService service = new AiRequestGuardService(
                guardProperties(),
                fixedClock()
        );

        AiRequestDecision first = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );
        AiRequestDecision second = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );
        AiRequestDecision third = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );
        AiRequestDecision fourth = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );
        AiRequestDecision fifth = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );
        AiRequestDecision sixth = service.evaluate(
                SecurityContextHolder.getContext().getAuthentication()
        );

        assertTrue(first.allowed());
        assertTrue(second.allowed());
        assertTrue(third.allowed());
        assertTrue(fourth.allowed());
        assertTrue(fifth.allowed());
        assertEquals(AiFailureCode.RATE_LIMITED, sixth.failureCode());
    }

    @Test
    void evaluateCurrentRequest_WhenAuthIsOptional_AllowsAnonymousCaller() {
        SecurityContextHolder.clearContext();

        AiGuardProperties properties = guardProperties();
        properties.setAuthRequired(false);
        AiRequestGuardService service = new AiRequestGuardService(
                properties,
                fixedClock()
        );

        AiRequestDecision decision = service.evaluateCurrentRequest();

        assertTrue(decision.allowed());
    }

    private AiGuardProperties guardProperties() {
        AiGuardProperties properties = new AiGuardProperties();
        properties.setAuthRequired(true);
        properties.setMaxRequestsPerWindow(5);
        properties.setRateLimitWindowSeconds(60);
        return properties;
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-04-03T11:45:00Z"), ZoneOffset.UTC);
    }
}
