package com.junaldadlawan.event_ticketing_api.seatmap.repository;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.SeatMap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SeatMapRepository extends JpaRepository<SeatMap, UUID> {

    // SeatMap extends Auditable (full-audit/soft-delete tier per the ERD),
    // so this excludes soft-deleted rows for consistency with the rest of
    // the codebase's list/lookup conventions, even though no delete
    // endpoint for SeatMap exists yet in this phase.
    Optional<SeatMap> findByEventIdAndDeletedAtIsNull(UUID eventId);
}
