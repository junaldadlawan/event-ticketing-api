package com.junaldadlawan.event_ticketing_api.ticket.repository;

import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Backs the per-event ticket-number suffix generation retry loop in
     * {@code CheckoutServiceImpl} (BR-TICKET-005: the 6-character suffix only
     * needs to be unique within its own event, not platform-wide).
     */
    boolean existsByEventIdAndTicketNumber(UUID eventId, String ticketNumber);

    List<Ticket> findByOrderId(UUID orderId);

    /**
     * Used to resolve "this order's event" for order-visibility checks
     * (confirmed decision #3 — Order has no eventId column of its own).
     */
    Optional<Ticket> findFirstByOrderId(UUID orderId);

    List<Ticket> findByOwnerId(UUID ownerId);

    /**
     * Supports {@code GET /events/{eventId}/orders}: Order has no eventId
     * column, so the distinct set of order ids for an event is resolved via
     * its issued tickets instead.
     */
    @Query("select distinct t.orderId from Ticket t where t.eventId = :eventId")
    List<UUID> findDistinctOrderIdsByEventId(@Param("eventId") UUID eventId);
}
