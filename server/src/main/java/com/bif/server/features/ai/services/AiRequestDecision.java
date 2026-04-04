package com.bif.server.features.ai.services;

import com.bif.server.features.ai.dto.graphql.AiFailureCode;

public record AiRequestDecision(
        boolean allowed,
        String userId,
        AiFailureCode failureCode,
        String message) {

    public static AiRequestDecision allowed(String userId) {
        return new AiRequestDecision(true, userId, null, null);
    }

    public static AiRequestDecision denied(AiFailureCode failureCode, String message) {
        return new AiRequestDecision(false, null, failureCode, message);
    }
}
