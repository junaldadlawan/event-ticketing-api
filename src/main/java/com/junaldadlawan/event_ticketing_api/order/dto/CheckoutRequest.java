package com.junaldadlawan.event_ticketing_api.order.dto;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * Matches openapi.yaml's checkout request body ({@code payment_method_token}
 * - camelCase here per this codebase's established JSON convention, see
 * {@code CartItemCreateRequest}). Client-side token from the payment
 * gateway; the API never receives raw card data (BR-PAY-001).
 */
public record CheckoutRequest(
        @NotBlank
        String paymentMethodToken) implements Serializable {
}
