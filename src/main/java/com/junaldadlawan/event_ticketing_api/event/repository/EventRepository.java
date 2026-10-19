package com.junaldadlawan.event_ticketing_api.event.repository;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import com.junaldadlawan.event_ticketing_api.event.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    boolean existsByTicketPrefix(String ticketPrefix);

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /** Phase 14 (BR-ANALYTICS-002): "event volume" - total events ever created, platform-wide, not just currently-published ones. */
    long countByDeletedAtIsNull();

    /** BR-EVENT-005: live events whose end time has passed, for the completion sweep. */
    @Query("select e.id from Event e where e.status in :statuses and e.endAt <= :now and e.deletedAt is null")
    List<UUID> findEndedIds(@Param("statuses") Collection<EventStatus> statuses, @Param("now") Instant now);

    /**
     * Moves one ended event to COMPLETED only if it is still live, as a single conditional UPDATE, so the job and a
     * read racing on the same event complete it once (the one that gets 1 back records it).
     */
    @Modifying
    @Transactional
    @Query("update Event e set e.status = :completed, e.updatedAt = :now"
            + " where e.id = :id and e.status in :statuses and e.endAt <= :now and e.deletedAt is null")
    int markCompletedIfEnded(@Param("id") UUID id, @Param("statuses") Collection<EventStatus> statuses,
                             @Param("completed") EventStatus completed, @Param("now") Instant now);
}
