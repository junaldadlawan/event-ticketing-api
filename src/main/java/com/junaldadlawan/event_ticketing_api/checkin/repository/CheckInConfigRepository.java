package com.junaldadlawan.event_ticketing_api.checkin.repository;

import com.junaldadlawan.event_ticketing_api.checkin.entity.CheckInConfig;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CheckInConfigRepository extends JpaRepository<CheckInConfig, UUID> {
    Optional<CheckInConfig> findByEventId(UUID eventId);

    /**
     * Code-reviewer HIGH (Phase 10 review): locks the event's config row so
     * concurrent {@code ScannerDeviceServiceImpl.authorize} calls in
     * pure_offline mode actually serialize against each other, instead of
     * both reading "no active device yet" and both inserting one - same
     * idiom as {@code OrderRepository}/{@code TicketRepository}'s
     * {@code findByIdForUpdate}. No matching row means no lock is taken
     * (harmless): an absent config row means STANDARD mode, which has no
     * single-active-device invariant to protect.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CheckInConfig c where c.eventId = :eventId")
    Optional<CheckInConfig> findByEventIdForUpdate(@Param("eventId") UUID eventId);
}
