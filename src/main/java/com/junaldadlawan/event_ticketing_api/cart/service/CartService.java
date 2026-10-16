package com.junaldadlawan.event_ticketing_api.cart.service;

import com.junaldadlawan.event_ticketing_api.cart.dto.CartItemCreateRequest;
import com.junaldadlawan.event_ticketing_api.cart.dto.CartResponse;

import java.util.List;
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

    /**
     * As {@link #get(UUID)} but priced for only the given items (null = the whole cart): same shape and fee rules as checkout. 400 when an id is
     * not in the cart (or empty list), 409 when a selected item's hold has expired. A promo code counts only if it fits the selected items.
     */
    CartResponse get(UUID cartId, List<UUID> itemIds);

    /** Same as {@link #get} for a named buyer instead of the logged-in caller: used by the payment webhook, which has no login. */
    CartResponse getForBuyer(UUID cartId, UUID buyerId);

    CartResponse getForBuyer(UUID cartId, UUID buyerId, List<UUID> itemIds);

    CartResponse addItem(UUID cartId, CartItemCreateRequest request);

    void removeItem(UUID cartId, UUID itemId);

    /**
     * Sets the quantity of a general-admission line, adjusting the held inventory by the difference. The hold expiry is kept (a change never
     * extends it). 409 for a seated line, an expired hold, more than is available, or more than the ticket type's per-order limit.
     */
    CartResponse updateItemQuantity(UUID cartId, UUID itemId, int quantity);

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
