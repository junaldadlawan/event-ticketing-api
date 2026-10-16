package com.junaldadlawan.event_ticketing_api.order.dto;

import com.junaldadlawan.event_ticketing_api.order.entity.PendingCheckout;
import com.junaldadlawan.event_ticketing_api.order.enums.PendingCheckoutStatus;

import java.io.Serializable;
import java.util.UUID;

/**
 * Answer to a checkout that waits for the payment provider (PayMongo): the web app attaches the buyer's card to the payment intent
 * with {@code clientKey} (the card never touches this server), follows any 3-D Secure redirect, then polls
 * {@code GET /checkouts/{checkoutId}} until the status is PAID (the order id is then set). {@code clientKey} is a secret for this
 * one payment: do not log it.
 */
public record CheckoutStartResponse(
        UUID checkoutId,
        PendingCheckoutStatus status,
        String clientKey,
        long amount,
        String currency,
        UUID orderId,
        String failureReason,
        String nextAction,
        String redirectUrl,
        String cardSaving) implements Serializable {

    public CheckoutStartResponse(UUID checkoutId, PendingCheckoutStatus status, String clientKey, long amount, String currency,
                                 UUID orderId, String failureReason, String nextAction, String redirectUrl) {
        this(checkoutId, status, clientKey, amount, currency, orderId, failureReason, nextAction, redirectUrl, "NOT_REQUESTED");
    }

    /** For a card typed in the browser: no next action is known to the server. */
    public CheckoutStartResponse(UUID checkoutId, PendingCheckoutStatus status, String clientKey, long amount, String currency,
                                 UUID orderId, String failureReason) {
        this(checkoutId, status, clientKey, amount, currency, orderId, failureReason, null, null);
    }

    public static CheckoutStartResponse from(PendingCheckout checkout) {
        // saved card paid from the server: NONE = nothing more to do (the webhook completes the order), REDIRECT = the bank's 3-D Secure page
        String next = checkout.getAttachedAt() == null ? null : checkout.getRedirectUrl() == null ? "NONE" : "REDIRECT";
        return new CheckoutStartResponse(checkout.getId(), checkout.getStatus(), checkout.getClientKey(), checkout.getAmount(),
                checkout.getCurrency(), checkout.getOrderId(), checkout.getFailureReason(), next, checkout.getRedirectUrl(), checkout.getCardSaving());
    }
}
