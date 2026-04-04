package com.bif.server.features.ai.exceptions;

public class AiParseException extends AiIntegrationException {

    public AiParseException(String message) {
        super(message);
    }

    public AiParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
