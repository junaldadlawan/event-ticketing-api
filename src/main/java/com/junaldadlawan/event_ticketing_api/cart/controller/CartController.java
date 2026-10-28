package com.junaldadlawan.event_ticketing_api.cart.controller;

import com.junaldadlawan.event_ticketing_api.cart.dto.ApplyPromoCodeRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemQuantityRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import com.junaldadlawan.event_ticketing_api.common.exception.TooManyRequestsException;
import com.junaldadlawan.event_ticketing_api.common.ratelimit.RateLimiter;
import com.junaldadlawan.event_ticketing_api.organization.security.OrganizationAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final RateLimiter rateLimiter;
    private final OrganizationAccessGuard accessGuard;

    // BR-NFR-004: cap promo-code tries per buyer so a code can't be brute-forced.
    @Value("${app.ratelimit.promo-per-minute:20}")
    private int promoPerMinute;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse create() {
        return cartService.create();
    }

    @GetMapping("/{cartId}")
    public CartResponse get(@PathVariable UUID cartId, @RequestParam(required = false) List<UUID> itemIds) {
        return cartService.get(cartId, itemIds);
    }

    @PostMapping("/{cartId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(@PathVariable UUID cartId, @Valid @RequestBody CartItemCreateRequest request) {
        return cartService.addItem(cartId, request);
    }

    @PatchMapping("/{cartId}/items/{itemId}")
    public CartResponse updateItemQuantity(@PathVariable UUID cartId, @PathVariable UUID itemId,
                                           @Valid @RequestBody CartItemQuantityRequest request) {
        return cartService.updateItemQuantity(cartId, itemId, request.quantity());
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable UUID cartId, @PathVariable UUID itemId) {
        cartService.removeItem(cartId, itemId);
    }

    @PostMapping("/{cartId}/promo-code")
    public CartResponse applyPromoCode(@PathVariable UUID cartId, @Valid @RequestBody ApplyPromoCodeRequest request) {
        if (!rateLimiter.tryAcquire("promo", accessGuard.currentUserId().toString(), promoPerMinute)) {
            throw new TooManyRequestsException("Too many promo-code attempts. Try again in a minute.");
        }
        return cartService.applyPromoCode(cartId, request.code());
    }

    @DeleteMapping("/{cartId}/promo-code")
    public CartResponse removePromoCode(@PathVariable UUID cartId) {
        return cartService.removePromoCode(cartId);
    }
}
