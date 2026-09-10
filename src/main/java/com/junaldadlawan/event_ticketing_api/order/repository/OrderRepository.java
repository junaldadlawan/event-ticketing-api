package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.Order;
import com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus;
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
}
