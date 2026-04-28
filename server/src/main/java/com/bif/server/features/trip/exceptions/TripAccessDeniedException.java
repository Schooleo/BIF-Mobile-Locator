package com.bif.server.features.trip.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class TripAccessDeniedException extends RuntimeException {
    public TripAccessDeniedException(String message) {
        super(message);
    }
}
