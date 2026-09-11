package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Row lock for Phase 8's refund paths (code-reviewer CRITICAL) -
     * {@code RefundServiceImpl.createRefund} and {@code
     * refundAllForEventCancellation} both read-then-write cumulative
     * refunded amount and {@code Order.status}; without this, two
     * concurrent refund attempts on the same order (two admins, or an
     * admin racing a concurrent event cancellation) could both read "not
     * yet refunded" and both succeed, refunding the order twice over. Same
     * idiom as {@code CartRepository}/{@code TicketRepository}/{@code
     * ResaleListingRepository}'s {@code findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

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
