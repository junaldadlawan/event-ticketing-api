package com.junaldadlawan.event_ticketing_api.order.service;

import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;

import java.util.List;
import java.util.UUID;

public interface CheckoutService {

    /**
     * Charges the cart and, on success, creates a PAID Order + COMPLETED
     * Payment and converts each hold into its final sold state. See
     * CheckoutServiceImpl for the full idempotency/failure-handling
     * contract (matches openapi.yaml's 201/402/410 responses).
     */
    default OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken) {
        return checkout(cartId, idempotencyKey, paymentMethodToken, null, null);
    }

    /** Same, charging a saved card: with Stripe the token is the {@code pm_...} id and {@code customerId} the buyer's Customer. */
    default OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken, String customerId) {
        return checkout(cartId, idempotencyKey, paymentMethodToken, customerId, null);
    }

    /**
     * Same, paying only the cart items in {@code itemIds} (null = the whole cart): the order, the fees and the charge are for those items,
     * the others stay in the cart with their holds. The same idempotency key with another selection is a 409.
     */
    OrderResponse checkout(UUID cartId, UUID idempotencyKey, String paymentMethodToken, String customerId, List<UUID> itemIds);

    /** True when the payment provider confirms by webhook (PayMongo): checkout is then {@link #startCheckout}, not {@link #checkout}. */
    boolean isAsynchronous();

    /**
     * Starts a checkout the buyer completes in the browser: creates the provider's payment intent for the cart's total and a pending
     * checkout. No order exists yet. A repeated call with the same idempotency key returns the same checkout.
     */
    default CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey) {
        return startCheckout(cartId, idempotencyKey, null);
    }

    default CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey, List<UUID> itemIds) {
        return startCheckout(cartId, idempotencyKey, itemIds, false);
    }

    /**
     * As above for only the cart items in {@code itemIds} (null = the whole cart); the selection is kept for the webhook. {@code saveCard}
     * vaults the card the buyer types in the browser on their PayMongo customer once the payment succeeds.
     */
    CheckoutStartResponse startCheckout(UUID cartId, UUID idempotencyKey, List<UUID> itemIds, boolean saveCard);

    /**
     * Pays a started checkout with a saved card from the server: sets the security code on the payment method (never stored or logged) and
     * attaches it to the intent. The answer says whether the bank wants 3-D Secure (a redirect URL) or nothing more is needed (the webhook
     * completes the order). A declined card is a 402 and the cart stays as it is. A repeated call for the same checkout changes nothing.
     */
    CheckoutStartResponse attachSavedCard(UUID checkoutId, String paymentMethodId, String securityCode, String returnUrl);

    /** Webhook: the payment succeeded. Issues the order and tickets, or refunds the payment if the order can no longer be issued. Idempotent. */
    void onPaymentPaid(String providerIntentId, String providerPaymentId, long amount, String currency, String cardCountry);

    /** As {@link #onPaymentPaid(String, String, long, String, String)} when the gateway did not say where the card was issued. */
    default void onPaymentPaid(String providerIntentId, String providerPaymentId, long amount, String currency) {
        onPaymentPaid(providerIntentId, providerPaymentId, amount, currency, null);
    }

    /** Webhook: the payment failed. Nothing was charged; the buyer can retry. Idempotent. */
    void onPaymentFailed(String providerIntentId, String reason);

    /** The caller's own pending checkout (a checkout of another buyer is a 404). */
    CheckoutStartResponse getStatus(UUID checkoutId);
}
