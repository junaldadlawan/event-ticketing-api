package com.junaldadlawan.event_ticketing_api.event.repository;

import com.junaldadlawan.event_ticketing_api.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    boolean existsByTicketPrefix(String ticketPrefix);

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /** Phase 14 (BR-ANALYTICS-002): "event volume" - total events ever created, platform-wide, not just currently-published ones. */
    long countByDeletedAtIsNull();
}
