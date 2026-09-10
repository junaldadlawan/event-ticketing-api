package com.junaldadlawan.event_ticketing_api.order.controller;

import com.junaldadlawan.event_ticketing_api.order.dto.CheckoutRequest;
import com.junaldadlawan.event_ticketing_api.order.dto.OrderResponse;
import com.junaldadlawan.event_ticketing_api.order.service.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 * {@code EventPromoCodeController} is split from {@code EventController}) -
 * {@code POST /carts/{cartId}/checkout} delegates to {@link CheckoutService},
 * not {@code CartService}. Security: covered by SecurityConfig's existing
 * {@code /api/v1/carts/**} authenticated matcher, no new matcher needed.
 */
@RestController
@RequestMapping("/api/v1/carts/{cartId}/checkout")
@RequiredArgsConstructor
public class CartCheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse checkout(@PathVariable UUID cartId,
                                   @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                   @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(cartId, idempotencyKey, request.paymentMethodToken());
    }
}
