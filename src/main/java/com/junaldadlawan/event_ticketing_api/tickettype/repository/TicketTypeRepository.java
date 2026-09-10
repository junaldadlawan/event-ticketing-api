package com.junaldadlawan.event_ticketing_api.tickettype.repository;

import com.junaldadlawan.event_ticketing_api.tickettype.entity.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdAndDeletedAtIsNull(UUID eventId);

    Optional<TicketType> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Row-locking finder (Phase 5a cart/hold concurrency). Used by
     * {@code CartServiceImpl} to serialize concurrent GA add-to-cart
     * attempts against the same ticket type's {@code quantityAvailable}
     * column (BR-INV-005/006, BR-NFR-001).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketType t where t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") UUID id);
}
