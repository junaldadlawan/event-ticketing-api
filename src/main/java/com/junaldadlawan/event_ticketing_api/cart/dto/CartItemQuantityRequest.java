package com.junaldadlawan.event_ticketing_api.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;

/** Body of {@code PATCH /carts/{cartId}/items/{itemId}}: the new quantity of a general-admission line (1 or more; use DELETE to remove it). */
public record CartItemQuantityRequest(
        @NotNull
        @Positive
        Integer quantity) implements Serializable {
}
