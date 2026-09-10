package com.junaldadlawan.event_ticketing_api.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.io.Serializable;
import java.util.UUID;

/**
 * Matches openapi.yaml's {@code addCartItem} request body. {@code seatId} is
 * only required for RESERVED_SEATING ticket types (checked at the service
 * layer, since which fields are required depends on the referenced ticket
 * type's kind). {@code quantity} defaults to 1 (openapi: {@code default: 1})
 * and is ignored when {@code seatId} is set (one CartItem = one seat).
 */
public record CartItemCreateRequest(
        @NotNull
        UUID ticketTypeId,

        UUID seatId,

        @Positive
        Integer quantity) implements Serializable {
}
