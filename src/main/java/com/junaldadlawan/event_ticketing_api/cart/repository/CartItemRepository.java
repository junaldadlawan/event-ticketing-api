package com.junaldadlawan.event_ticketing_api.cart.repository;

import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findByCartId(UUID cartId);

    /** Used by the lazy expired-hold-release logic in CartServiceImpl. */
    List<CartItem> findByTicketTypeIdAndHoldExpiresAtBefore(UUID ticketTypeId, Instant now);
}
