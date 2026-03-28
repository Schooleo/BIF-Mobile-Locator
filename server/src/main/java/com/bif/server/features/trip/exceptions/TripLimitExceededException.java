package com.bif.server.features.trip.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TripLimitExceededException extends RuntimeException {
    public TripLimitExceededException(String message) {
        super(message);
    }
}
