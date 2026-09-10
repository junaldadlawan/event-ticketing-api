package com.junaldadlawan.event_ticketing_api.cart.dto;

import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** DTO for {@link CartItem}, matching openapi.yaml's {@code CartItem} schema. */
public record CartItemResponse(
        UUID id,
        UUID ticketTypeId,
        UUID seatId,
        int quantity,
        Instant holdExpiresAt,
        Instant createdAt) implements Serializable {

    public static CartItemResponse from(CartItem cartItem) {
        return new CartItemResponse(
                cartItem.getId(),
                cartItem.getTicketTypeId(),
                cartItem.getSeatId(),
                cartItem.getQuantity(),
                cartItem.getHoldExpiresAt(),
                cartItem.getCreatedAt());
    }
}
