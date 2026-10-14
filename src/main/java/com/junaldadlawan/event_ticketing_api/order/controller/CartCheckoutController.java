package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.common.exception.BadRequestException;
import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutRequest;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import com.junaldadlawan.event_ticketing_api.paymentmethod.service.PaymentMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * With the mock or Stripe gateway this charges at once and answers 201 with the order. With PayMongo the card is entered in the
     * browser, so this only starts the payment (202 with the client key); the order exists once the webhook confirms the payment
     * ({@code GET /checkouts/{id}} shows the status).
     */
    @PostMapping
    public ResponseEntity<Object> checkout(@PathVariable UUID cartId,
                                           @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                           @Valid @RequestBody(required = false) CheckoutRequest request) {
        if (checkoutService.isAsynchronous()) {
            return ResponseEntity.accepted().body(checkoutService.startCheckout(cartId, idempotencyKey));
        }
        if (request == null) {
            throw new BadRequestException("Provide either paymentMethodToken or paymentMethodId");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(chargeNow(cartId, idempotencyKey, request));
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
            return checkoutService.checkout(cartId, idempotencyKey, card.token(), card.customerId());
        }
        return checkoutService.checkout(cartId, idempotencyKey, request.paymentMethodToken());
    }
}
