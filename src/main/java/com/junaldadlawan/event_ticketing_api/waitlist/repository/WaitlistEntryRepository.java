package com.junaldadlawan.event_ticketing_api.waitlist.repository;

import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

    /**
     * {@code GET /users/me/waitlist-entries} - the caller's own entries,
     * oldest-joined first.
     */
    List<WaitlistEntry> findByUserIdOrderByCreatedAtAsc(UUID userId);

    // Split into ticketTypeId-present/-absent pairs rather than one method
    // taking a nullable UUID - a derived `...AndTicketTypeId(id)` query
    // compiles to `= :id`, which (per standard SQL three-valued logic) never
    // matches a NULL column even when the Java argument itself is null.

    boolean existsByEventIdAndTicketTypeIdAndUserId(UUID eventId, UUID ticketTypeId, UUID userId);

    boolean existsByEventIdAndTicketTypeIdIsNullAndUserId(UUID eventId, UUID userId);

    long countByEventIdAndTicketTypeId(UUID eventId, UUID ticketTypeId);

    long countByEventIdAndTicketTypeIdIsNull(UUID eventId);

    /**
     * BR-WAIT-002 (Phase 11): the earliest-joined, not-yet-notified entry
     * for a specific ticket type - who to offer a just-freed-up unit of
     * inventory to next.
     * <p>
     * Code-reviewer HIGH: locked (not a plain derived query) because the
     * only OTHER serialization in play - {@code TicketTypeRepository
     * .findByIdForUpdate}, taken by the caller before this - is scoped to
     * one specific ticket-type row. Two concurrent refunds for DIFFERENT GA
     * ticket types of the SAME event lock different TicketType rows and
     * don't serialize against each other at all, so without a lock here,
     * both could read the same "earliest un-notified" row before either
     * commits and both notify the same person twice while the next waiter
     * is skipped. {@code PESSIMISTIC_WRITE} + a real DB row lock means a
     * second concurrent transaction blocks here, then - per Postgres's
     * documented {@code FOR UPDATE} re-check semantics - re-evaluates the
     * WHERE clause against the row's new committed state, correctly moving
     * on to the next candidate if the first transaction already claimed it.
     * A {@code Pageable} of size 1 stands in for {@code findFirst}, since
     * {@code @Query} + {@code @Lock} don't combine with the derived-query
     * {@code First} keyword.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntry w where w.eventId = :eventId and w.ticketTypeId = :ticketTypeId and w.notifiedAt is null order by w.position asc")
    List<WaitlistEntry> findNextNotNotifiedForTicketTypeForUpdate(@Param("eventId") UUID eventId, @Param("ticketTypeId") UUID ticketTypeId, Pageable pageable);

    /** Same as above, for an event-general (no specific ticket type) join - see that method's javadoc for why this is locked too. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitlistEntry w where w.eventId = :eventId and w.ticketTypeId is null and w.notifiedAt is null order by w.position asc")
    List<WaitlistEntry> findNextNotNotifiedEventGeneralForUpdate(@Param("eventId") UUID eventId, Pageable pageable);
}
