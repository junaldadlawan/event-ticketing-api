package com.junaldadlawan.event_ticketing_api.ticket.repository;

import com.junaldadlawan.event_ticketing_api.ticket.dto.MyTicketEventResponse;
import com.junaldadlawan.event_ticketing_api.ticket.entity.Ticket;
import com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByTicketTypeId(UUID ticketTypeId);

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
     * {@code GET /users/me/tickets}: what the owner holds now, whatever order it came from. Joined to its event
     * so a page can be sorted by {@code e.startAt}. A null {@code eventId} means every event.
     */
    @Query(value = "select t from Ticket t join Event e on e.id = t.eventId where t.ownerId = :ownerId and t.status in :statuses"
            + " and (:eventId is null or t.eventId = :eventId)",
            countQuery = "select count(t) from Ticket t where t.ownerId = :ownerId and t.status in :statuses"
                    + " and (:eventId is null or t.eventId = :eventId)")
    Page<Ticket> findHeldBy(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<TicketStatus> statuses,
                            @Param("eventId") UUID eventId, Pageable pageable);

    /** {@code GET /users/me/tickets/events}: the events the owner holds tickets for now, with how many, soonest first. */
    @Query("select new com.junaldadlawan.event_ticketing_api.ticket.dto.MyTicketEventResponse(e.id, e.title, e.startAt, e.timezone, count(t))"
            + " from Ticket t join Event e on e.id = t.eventId where t.ownerId = :ownerId"
            + " group by e.id, e.title, e.startAt, e.timezone order by e.startAt asc")
    List<MyTicketEventResponse> findHeldEvents(@Param("ownerId") UUID ownerId);

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

    /**
     * BR-TICKET-LIMIT-001: tickets of a type the buyer bought (their paid orders), minus refunded or cancelled ones.
     * Counted by the order's buyer, so tickets given away still count and tickets received do not.
     */
    @Query("select count(t) from Ticket t join Order o on o.id = t.orderId where o.buyerId = :buyerId and t.ticketTypeId = :ticketTypeId"
            + " and o.status in (com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus.PAID,"
            + " com.junaldadlawan.event_ticketing_api.order.enums.OrderStatus.PARTIALLY_REFUNDED)"
            + " and t.status not in (com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus.REFUNDED,"
            + " com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus.CANCELLED)")
    long countBoughtBy(@Param("buyerId") UUID buyerId, @Param("ticketTypeId") UUID ticketTypeId);

    /** BR-NOTIFY-002: who holds a ticket for the event now (its current owners, not its buyers). */
    @Query("select distinct t.ownerId from Ticket t where t.eventId = :eventId and t.status in :statuses")
    List<UUID> findHolderIds(@Param("eventId") UUID eventId, @Param("statuses") Collection<TicketStatus> statuses);

    /** BR-NOTIFY-002 GATE_OPEN: {@code [ownerId, the largest gate_opens_minutes_before of the types they hold]}. */
    @Query("select t.ownerId, max(tt.gateOpensMinutesBefore) from Ticket t join TicketType tt on tt.id = t.ticketTypeId"
            + " where t.eventId = :eventId and t.status in :statuses and tt.gateOpensMinutesBefore is not null group by t.ownerId")
    List<Object[]> findHolderGateMinutes(@Param("eventId") UUID eventId, @Param("statuses") Collection<TicketStatus> statuses);

    /** BR-NOTIFY-002 REVIEW_REQUEST: owners of a USED ticket for the event who have no review of it. */
    @Query("select distinct t.ownerId from Ticket t where t.eventId = :eventId"
            + " and t.status = com.junaldadlawan.event_ticketing_api.ticket.enums.TicketStatus.USED"
            + " and not exists (select r.id from EventReview r where r.eventId = :eventId and r.userId = t.ownerId and r.deletedAt is null)")
    List<UUID> findAttendeesWithoutReview(@Param("eventId") UUID eventId);

    /** BR-REVIEW-001: only someone holding a checked-in (USED) ticket for the event may review it. */
    boolean existsByEventIdAndOwnerIdAndStatus(UUID eventId, UUID ownerId, TicketStatus status);

    /** Phase 10: {@code GET /scanner-devices/{deviceId}/dataset} - the full pre-fetch for offline validation. */
    List<Ticket> findByEventId(UUID eventId);

    /**
     * Phase 14 (BR-ANALYTICS-001): "tickets sold" - every {@code Ticket} row
     * for the event, regardless of its current status (a later transfer/
     * refund doesn't undo the fact that a sale happened - {@code Ticket}
     * rows are only ever created on a successful checkout).
     */
    long countByEventId(UUID eventId);

    /**
     * Phase 14: per-day ticket counts for {@code sales_over_time}. Each row
     * is {@code [LocalDate, Long]}. {@code cast(... as date)} is an ANSI SQL
     * function Hibernate translates directly (no native query needed, so no
     * risk of a wrong hand-typed column name).
     */
    @Query("select cast(t.createdAt as date), count(t) from Ticket t where t.eventId = :eventId group by cast(t.createdAt as date) order by cast(t.createdAt as date)")
    List<Object[]> countGroupedByDayForEvent(@Param("eventId") UUID eventId);
}
