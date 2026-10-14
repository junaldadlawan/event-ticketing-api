package com.junaldadlawan.event_ticketing_api.order.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * Matches openapi.yaml's checkout request body ({@code payment_method_token}
 * - camelCase here per this codebase's established JSON convention, see
 * {@code CartItemCreateRequest}). Client-side token from the payment
 * gateway; the API never receives raw card data (BR-PAY-001). Exactly one of the two fields is given:
 * {@code paymentMethodToken} (a one-off token) or {@code paymentMethodId} (a card the signed-in user saved under
 * {@code /users/me/payment-methods}); the controller enforces that.
 */
public record CheckoutRequest(
        String paymentMethodToken,
        UUID paymentMethodId) implements Serializable {
}
