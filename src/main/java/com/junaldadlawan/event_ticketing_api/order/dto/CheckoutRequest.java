package com.junaldadlawan.event_ticketing_api.order.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Matches openapi.yaml's checkout request body ({@code payment_method_token}
 * - camelCase here per this codebase's established JSON convention, see
 * {@code CartItemCreateRequest}). Client-side token from the payment
 * gateway; the API never receives raw card data (BR-PAY-001). Exactly one of the two fields is given:
 * {@code paymentMethodToken} (a one-off token) or {@code paymentMethodId} (a card the signed-in user saved under
 * {@code /users/me/payment-methods}); the controller enforces that. {@code securityCode} (3 or 4 digits, only for a saved card; never stored or logged), {@code returnUrl} (where the bank sends the buyer back after 3-D Secure, for a saved card), {@code saveCard} (true = vault the card typed in the browser for later), {@code itemIds} (optional) pays only those cart items: omitted = the whole cart.
 */
public record CheckoutRequest(
        String paymentMethodToken,
        UUID paymentMethodId,
        List<UUID> itemIds,
        @Pattern(regexp = "\\d{3,4}", message = "must be 3 or 4 digits") String securityCode,
        Boolean saveCard,
        @Size(max = 500) String returnUrl) implements Serializable {

    /** Pays the whole cart. */
    public CheckoutRequest(String paymentMethodToken, UUID paymentMethodId) {
        this(paymentMethodToken, paymentMethodId, null, null, null, null);
    }

    public CheckoutRequest(String paymentMethodToken, UUID paymentMethodId, List<UUID> itemIds) {
        this(paymentMethodToken, paymentMethodId, itemIds, null, null, null);
    }

    /** The security code is never part of the text form, so it cannot end up in a log. */
    @Override
    public String toString() {
        return "CheckoutRequest[paymentMethodId=" + paymentMethodId + ", itemIds=" + itemIds + ", saveCard=" + saveCard + "]";
    }
}
