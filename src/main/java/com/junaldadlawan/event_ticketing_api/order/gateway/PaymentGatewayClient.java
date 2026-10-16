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

    /**
     * True when the provider confirms a payment later, by webhook, after the buyer's card is attached in the browser (PayMongo).
     * Checkout then starts a payment intent ({@link #createPaymentIntent}) and the order is issued when the webhook says paid,
     * instead of charging in one synchronous call.
     */
    default boolean confirmsByWebhook() {
        return false;
    }

    /** Starts a payment the buyer will complete in the browser. Only for gateways where {@link #confirmsByWebhook()} is true. */
    default PaymentIntentInfo createPaymentIntent(Money amount, String description, String reference) {
        throw new UnsupportedOperationException("This payment gateway charges synchronously");
    }

    /** As above, and the card paid with is vaulted on the provider's customer for later (PayMongo setup_future_usage, on-session). */
    default PaymentIntentInfo createPaymentIntent(Money amount, String description, String reference, String saveForCustomerId) {
        throw new UnsupportedOperationException("This payment gateway cannot save cards this way");
    }

    /** Creates the provider's customer a card is saved on. */
    default String createCustomer(String email, String firstName, String lastName) {
        throw new UnsupportedOperationException("This payment gateway has no customers");
    }

    /** The cards saved on the customer (description and payment-method id only). */
    default java.util.List<VaultedCard> customerCards(String customerId) {
        throw new UnsupportedOperationException("This payment gateway has no saved cards");
    }

    /**
     * Pays the intent with a saved card from the server: the security code (when given) is set on the payment method, then the method is
     * attached to the intent. Never throws for a declined card; {@link AttachOutcome#failureReason()} says why. The code is never stored or logged.
     */
    default AttachOutcome attachSavedCard(String intentId, String clientKey, String paymentMethodId, String securityCode, String returnUrl) {
        throw new UnsupportedOperationException("This payment gateway cannot charge a saved card");
    }

    /** A card saved on a provider customer. */
    record VaultedCard(String id, String brand, String last4, int expMonth, int expYear) {
    }

    /** What attaching a saved card did: {@code redirectUrl} is set when the bank asks for 3-D Secure; {@code failureReason} when it was declined. */
    record AttachOutcome(String status, String redirectUrl, String failureReason) {
    }

    /** The provider's payment intent id and the key the browser needs to attach a card to it. */
    record PaymentIntentInfo(String intentId, String clientKey) {
    }
}
