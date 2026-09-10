package com.junaldadlawan.event_ticketing_api.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 402 — the payment gateway declined the charge. Introduced in Phase 5b for
 * checkout's documented 402 (openapi.yaml's
 * {@code POST /carts/{cartId}/checkout}: "Payment failed; cart and holds
 * remain intact for retry").
 */
public class PaymentFailedException extends ApiException {
    public PaymentFailedException(String message) {
        super(HttpStatus.PAYMENT_REQUIRED, message);
    }
}
