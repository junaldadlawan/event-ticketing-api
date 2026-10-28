package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.common.exception.TooManyRequestsException;
import com.junaldadlawan.event_ticketing_api.common.ratelimit.RateLimiter;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutRequest;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutStartResponse;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Separate controller from {@code CartController} (matches how
 * {@code PromoCodeController} is split from {@code EventController}) -
 * {@code POST /carts/{cartId}/checkout} delegates to {@link CheckoutService},
 * not {@code CartService}. Security: covered by SecurityConfig's existing
 * {@code /api/v1/carts/**} authenticated matcher, no new matcher needed.
 */
@RestController
@RequestMapping("/api/v1/carts/{cartId}/checkout")
@RequiredArgsConstructor
public class CartCheckoutController {

    private final CheckoutService checkoutService;
    private final PaymentMethodService paymentMethodService;
    private final RateLimiter rateLimiter;
    private final OrganizationAccessGuard accessGuard;

    // BR-NFR-004: cap checkout attempts per buyer to deter payment-testing/scalping automation.
    @Value("${app.ratelimit.checkout-per-minute:15}")
    private int checkoutPerMinute;

    /**
     * With the mock or Stripe gateway this charges at once and answers 201 with the order. With PayMongo the card is entered in the
     * browser, so this only starts the payment (202 with the client key); the order exists once the webhook confirms the payment
     * ({@code GET /checkouts/{id}} shows the status).
     */
    @PostMapping
    public ResponseEntity<Object> checkout(@PathVariable UUID cartId,
                                           @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                           @Valid @RequestBody(required = false) CheckoutRequest request) {
        if (!rateLimiter.tryAcquire("checkout", accessGuard.currentUserId().toString(), checkoutPerMinute)) {
            throw new TooManyRequestsException("Too many checkout attempts. Try again in a minute.");
        }
        if (checkoutService.isAsynchronous()) {
            List<UUID> itemIds = request == null ? null : request.itemIds();
            if (request != null && request.paymentMethodId() != null) {
                return ResponseEntity.accepted().body(payWithSavedCard(cartId, idempotencyKey, request));
            }
            return ResponseEntity.accepted().body(checkoutService.startCheckout(cartId, idempotencyKey, itemIds,
                    request != null && Boolean.TRUE.equals(request.saveCard())));
        }
        if (request == null) {
            throw new BadRequestException("Provide either paymentMethodToken or paymentMethodId");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeNow(cartId, idempotencyKey, request));
    }

    /**
     * A saved card with PayMongo: the card is looked up first (another user's card is a 404, an old card without a stored payment method a
     * 409) so nothing is started for it; then the payment is started and the saved card attached from the server.
     */
    private CheckoutStartResponse payWithSavedCard(UUID cartId, UUID idempotencyKey, CheckoutRequest request) {
        if (request.returnUrl() == null || !request.returnUrl().matches("(?i)https?://\\S+")) {
            throw new BadRequestException("returnUrl (an http or https address) is required to pay with a saved card");
        }
        if (request.securityCode() == null) {
            throw new BadRequestException("securityCode is required to pay with a saved card");
        }
        PaymentMethodService.SavedCardCharge card = paymentMethodService.resolveForCheckout(request.paymentMethodId());
        CheckoutStartResponse started = checkoutService.startCheckout(cartId, idempotencyKey, request.itemIds(), false);
        return checkoutService.attachSavedCard(started.checkoutId(), card.token(), request.securityCode(), request.returnUrl());
    }

    private OrderResponse chargeNow(UUID cartId, UUID idempotencyKey, CheckoutRequest request) {
        boolean hasToken = request.paymentMethodToken() != null && !request.paymentMethodToken().isBlank();
        boolean hasSavedCard = request.paymentMethodId() != null;
        if (hasToken == hasSavedCard) {
            throw new BadRequestException("Provide either paymentMethodToken or paymentMethodId");
        }
        if (hasSavedCard) {
            // a card id of another user is a 404 here, so nobody can pay with someone else's card
            PaymentMethodService.SavedCardCharge card = paymentMethodService.resolveForCheckout(request.paymentMethodId());
            return checkoutService.checkout(cartId, idempotencyKey, card.token(), card.customerId(), request.itemIds());
        }
        return checkoutService.checkout(cartId, idempotencyKey, request.paymentMethodToken(), null, request.itemIds());
    }
}
