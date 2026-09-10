package com.junaldadlawan.event_ticketing_api.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 422 — request is well-formed but semantically inapplicable given current
 * state. Introduced in Phase 5a for promo-code application (openapi.yaml's
 * documented 422 on {@code POST /carts/{cartId}/promo-code}: "Code is
 * expired, exhausted, or inapplicable to the items in the cart").
 */
public class UnprocessableEntityException extends ApiException {
    public UnprocessableEntityException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
