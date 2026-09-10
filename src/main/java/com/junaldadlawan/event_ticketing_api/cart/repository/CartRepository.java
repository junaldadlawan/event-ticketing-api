package com.junaldadlawan.event_ticketing_api.cart.repository;

import com.junaldadlawan.event_ticketing_api.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    /**
     * Row-locking finder (Phase 5b checkout concurrency). Serializes two
     * concurrent {@code checkout()} attempts against the same cart - see
     * {@code CheckoutServiceImpl.doCheckout} for why this has to be acquired
     * before reading the cart's {@code CartItem}s (same pattern as
     * {@code TicketTypeRepository}/{@code SeatRepository}'s
     * {@code findByIdForUpdate}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(@Param("id") UUID id);
}
