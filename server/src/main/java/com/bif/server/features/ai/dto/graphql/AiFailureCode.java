package com.bif.server.features.ai.dto.graphql;

public enum AiFailureCode {
    UNAUTHORIZED,
    RATE_LIMITED,
    INVALID_QUERY,
    NO_RESULTS,
    NO_CANDIDATE_PLACES,
    AI_PARSE_FAILURE,
    AI_VALIDATION_FAILURE,
    AI_UPSTREAM_FAILURE,
    AI_FAILURE
}
