package com.junaldadlawan.event_ticketing_api.cart.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;

import java.util.UUID;

/**
 * Deliberate deviation from the rest of the codebase's Service-returns-
 * entity / Controller-maps-to-DTO convention: a Cart's response shape
 * (items + live-computed total + resolved applied promo code) requires
 * cross-referencing CartItem/TicketType/PromoCode, so assembling it is a
 * service-layer read-model concern, not a pure entity-to-DTO mapping. See
 * {@code CartServiceImpl.toResponse}.
 */
public interface CartService {

    CartResponse create();

    CartResponse get(UUID cartId);

    CartResponse addItem(UUID cartId, CartItemCreateRequest request);

    void removeItem(UUID cartId, UUID itemId);

    CartResponse applyPromoCode(UUID cartId, String code);

    CartResponse removePromoCode(UUID cartId);

    /**
     * Releases (deletes) every CartItem in this cart whose hold has expired,
     * restoring TicketType.quantityAvailable / flipping Seat back to
     * AVAILABLE as appropriate. Exposed (rather than kept private) so
     * CheckoutServiceImpl can reuse this exact lazy-release logic at
     * checkout's hold-expiry check instead of reimplementing it (Phase 5b).
     */
    void releaseExpiredHoldsForCart(UUID cartId);
}
