package com.junaldadlawan.event_ticketing_api.common.exception;

import org.springframework.http.HttpStatus;

/** 429 — the caller made too many requests in the current window (BR-NFR-004). Mapped by {@code GlobalExceptionHandler} like any {@link ApiException}. */
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
