package com.junaldadlawan.event_ticketing_api.cart.dto;

import com.junaldadlawan.event_ticketing_api.tickettype.dto.MoneyDto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-model for {@code Cart}, matching openapi.yaml's {@code Cart} schema.
 * Deliberately not a plain {@code from(Cart)} factory: {@code items},
 * {@code appliedPromoCode}, and {@code total} require cross-referencing
 * {@code CartItem}/{@code TicketType}/{@code PromoCode} and are computed
 * live by {@code CartServiceImpl}, not stored on {@code Cart} itself. {@code platformFee} is the platform fee that
 * will be added on top of the ticket price (after any promo discount; its fixed part is charged per ticket); {@code gatewayFee} is the payment
 * gateway's fee the buyer pays on top (0 unless management made the customer bear it); {@code total} is what the buyer will pay,
 * fees included. Priced for a selection ({@code ?itemIds=}), {@code items} and every amount are for those items only.
 */
public record CartResponse(
        UUID id,
        UUID buyerId,
        List<CartItemResponse> items,
        AppliedPromoCodeResponse appliedPromoCode,
        MoneyDto platformFee,
        MoneyDto gatewayFee,
        MoneyDto total,
        Instant createdAt,
        Instant updatedAt) implements Serializable {
}
