package com.junaldadlawan.event_ticketing_api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base type for exceptions that should be translated into a ProblemDetail response. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

}