package com.junaldadlawan.event_ticketing_api.ticket.repository;

import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Row lock for Phase 7's ownership-transfer paths (direct transfer and
     * resale purchase) - both mutate {@code owner_id}/{@code credential}/
     * {@code credentialVersion} on the same ticket row, and a resale-listing
     * purchase races against a concurrent direct transfer of the same
     * ticket. Same idiom as {@code CartRepository}/{@code SeatRepository}'s
     * {@code findByIdForUpdate}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") UUID id);

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

    /**
     * Phase 8: {@code GET /events/{eventId}/refund-policy}'s "a buyer with
     * an order on it" visibility branch - owning a ticket for the event
     * implies having an order for it (tickets are only ever issued via a
     * checkout/resale order), so this is equivalent to and cheaper than a
     * two-step order lookup.
     */
    boolean existsByEventIdAndOwnerId(UUID eventId, UUID ownerId);

    /** Phase 10: {@code GET /scanner-devices/{deviceId}/dataset} - the full pre-fetch for offline validation. */
    List<Ticket> findByEventId(UUID eventId);
}
