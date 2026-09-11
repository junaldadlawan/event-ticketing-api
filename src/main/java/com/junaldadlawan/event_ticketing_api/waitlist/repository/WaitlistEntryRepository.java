package com.junaldadlawan.event_ticketing_api.waitlist.repository;

import com.junaldadlawan.event_ticketing_api.waitlist.entity.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
