package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Used by PromoCodeUsageLimitGuard for BR-PROMO-002 (total usage limit). */
    long countByPromoCodeAndStatus(String promoCode, OrderStatus status);

    /** Used by PromoCodeUsageLimitGuard for BR-PROMO-006 (per-buyer usage limit). */
    long countByPromoCodeAndBuyerIdAndStatus(String promoCode, UUID buyerId, OrderStatus status);

    /**
     * At most one row is ever expected (see V11's partial unique index on
     * {@code cart_id}) - exposed as a List rather than Optional so a
     * violation of that invariant surfaces as more-than-one-element instead
     * of an ambiguous-result exception.
     */
    List<Order> findByCartId(UUID cartId);

    /** {@code GET /users/me/orders} (Phase 6a) — orders placed by the caller. */
    Page<Order> findByBuyerId(UUID buyerId, Pageable pageable);

    /**
     * {@code GET /events/{eventId}/orders} (Phase 6a) — Order has no eventId
     * column of its own (confirmed decision #3), so the caller first resolves
     * the distinct order ids for an event via {@code
     * TicketRepository.findDistinctOrderIdsByEventId}, then fetches the
     * matching Orders through this method.
     */
    Page<Order> findByIdIn(List<UUID> ids, Pageable pageable);
}
