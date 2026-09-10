package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;

import java.util.UUID;

public interface CheckoutService {

    /**
     * Charges the cart and, on success, creates a PAID Order + COMPLETED
     * Payment and converts each hold into its final sold state. See
     * CheckoutServiceImpl for the full idempotency/failure-handling
     * contract (matches openapi.yaml's 201/402/410 responses).
     */
    OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken);
}
