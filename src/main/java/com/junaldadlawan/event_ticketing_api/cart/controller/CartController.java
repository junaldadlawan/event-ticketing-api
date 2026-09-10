package com.junaldadlawan.event_ticketing_api.cart.controller;

import com.junaldadlawan.event_ticketing_api.cart.dto.ApplyPromoCodeRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;
import com.junaldadlawan.event_ticketing_api.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse create() {
        return cartService.create();
    }

    @GetMapping("/{cartId}")
    public CartResponse get(@PathVariable UUID cartId) {
        return cartService.get(cartId);
    }

    @PostMapping("/{cartId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse addItem(@PathVariable UUID cartId, @Valid @RequestBody CartItemCreateRequest request) {
        return cartService.addItem(cartId, request);
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItem(@PathVariable UUID cartId, @PathVariable UUID itemId) {
        cartService.removeItem(cartId, itemId);
    }

    @PostMapping("/{cartId}/promo-code")
    public CartResponse applyPromoCode(@PathVariable UUID cartId, @Valid @RequestBody ApplyPromoCodeRequest request) {
        return cartService.applyPromoCode(cartId, request.code());
    }

    @DeleteMapping("/{cartId}/promo-code")
    public CartResponse removePromoCode(@PathVariable UUID cartId) {
        return cartService.removePromoCode(cartId);
    }
}
