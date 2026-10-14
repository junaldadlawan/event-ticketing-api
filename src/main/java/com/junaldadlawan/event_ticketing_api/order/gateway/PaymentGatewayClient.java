package com.junaldadlawan.event_ticketing_api.order.gateway;

import com.junaldadlawan.event_ticketing_api.common.entity.Money;

/**
 * Provider-agnostic boundary to a payment gateway, matching openapi.yaml's
 * provider-agnostic {@code Payment} schema. {@link MockPaymentGatewayClient}
 * is the only implementation right now - a deterministic, no-external-call
 * stub, intentionally swappable for a real provider (Stripe/PayPal/etc)
 * later without touching any caller of this interface.
 */
public interface PaymentGatewayClient {

    /**
     * Attempts to charge {@code amount} against {@code paymentMethodToken}.
     * Never throws for a declined/failed charge - that's represented by
     * {@link PaymentResult#successful()} being {@code false}, so the caller
     * can distinguish "gateway call failed" business outcomes from actual
     * infrastructure errors.
     */
    PaymentResult charge(String paymentMethodToken, Money amount);

    /**
     * Charges a saved card: {@code paymentMethodToken} is the provider's payment-method id and {@code customerId} the
     * customer it is saved on. Gateways that do not need the customer (the mock) can keep this default.
     */
    default PaymentResult charge(String paymentMethodToken, String customerId, Money amount) {
        return charge(paymentMethodToken, amount);
    }

    /**
     * The checkout charge: {@code idempotencyKey} makes a retried checkout reuse the first charge instead of charging twice
     * (Stripe honours it; the mock ignores it). {@code customerId} is null for a one-off token.
     */
    default PaymentResult charge(String paymentMethodToken, String customerId, Money amount, String idempotencyKey) {
        return customerId == null ? charge(paymentMethodToken, amount) : charge(paymentMethodToken, customerId, amount);
    }

    /**
     * Reverses (fully or partially) a previously-{@code completed} charge,
     * identified by its own {@code gatewayRef} (Phase 8). Same
     * never-throws-on-decline contract as {@link #charge}.
     */
    PaymentResult refund(String gatewayRef, Money amount);
}
