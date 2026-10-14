package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
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

    /** Same, charging a saved card: with Stripe the token is the {@code pm_...} id and {@code customerId} the buyer's Customer. */
    OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken, String customerId);

    /** True when the payment provider confirms by webhook (PayMongo): checkout is then {@link #startCheckout}, not {@link #checkout}. */
    boolean isAsynchronous();

    /**
     * Starts a checkout the buyer completes in the browser: creates the provider's payment intent for the cart's total and a pending
     * checkout. No order exists yet. A repeated call with the same idempotency key returns the same checkout.
     */
    CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey);

    /** Webhook: the payment succeeded. Issues the order and tickets, or refunds the payment if the order can no longer be issued. Idempotent. */
    void onPaymentPaid(String providerIntentId, String providerPaymentId, long amount, String currency);

    /** Webhook: the payment failed. Nothing was charged; the buyer can retry. Idempotent. */
    void onPaymentFailed(String providerIntentId, String reason);

    /** The caller's own pending checkout (a checkout of another buyer is a 404). */
    CheckoutStartResponse getStatus(UUID checkoutId);
}
