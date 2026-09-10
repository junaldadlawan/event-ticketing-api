package com.junaldadlawan.event_ticketing_api.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 410 — the resource/state the request depended on existed but is no longer
 * available. Introduced in Phase 5b for checkout's documented 410
 * (openapi.yaml's {@code POST /carts/{cartId}/checkout}: "A hold expired
 * before checkout completed").
 */
public class GoneException extends ApiException {
    public GoneException(String message) {
        super(HttpStatus.GONE, message);
    }
}
