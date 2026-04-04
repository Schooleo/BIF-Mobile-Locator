package com.bif.server.features.ai.exceptions;

public class AiUpstreamException extends AiIntegrationException {

    public AiUpstreamException(String message) {
        super(message);
    }

    public AiUpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
