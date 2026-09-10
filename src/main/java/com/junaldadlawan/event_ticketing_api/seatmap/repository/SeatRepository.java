package com.junaldadlawan.event_ticketing_api.seatmap.repository;

import com.junaldadlawan.event_ticketing_api.seatmap.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findBySeatMapId(UUID seatMapId);

    /**
     * Row-locking finder (Phase 5a cart/hold concurrency). Used by
     * {@code CartServiceImpl} to serialize concurrent reserved-seating
     * add-to-cart attempts against the same seat's {@code status} column
     * (BR-INV-005, BR-NFR-001).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") UUID id);
}
