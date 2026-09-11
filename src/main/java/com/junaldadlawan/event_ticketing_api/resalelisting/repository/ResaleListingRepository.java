package com.junaldadlawan.event_ticketing_api.resalelisting.repository;

import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResaleListing;
import com.junaldadlawan.event_ticketing_api.resalelisting.enums.ResaleListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ResaleListingRepository extends JpaRepository<ResaleListing, UUID> {

    boolean existsByTicketIdAndStatus(UUID ticketId, ResaleListingStatus status);

    /**
     * Used by {@code TicketTransferServiceImpl.transfer} (code-reviewer
     * CRITICAL) to auto-cancel a ticket's own active listing whenever it's
     * directly transferred away - a listing must never outlive the
     * ownership state it was created against. At most one ACTIVE row can
     * ever match (V14's partial unique index), so {@code Optional} is exact,
     * not just "the first one".
     */
    Optional<ResaleListing> findByTicketIdAndStatus(UUID ticketId, ResaleListingStatus status);

    /** {@code GET /events/{eventId}/resale-listings} - public browse, active listings only. */
    Page<ResaleListing> findByEventIdAndStatus(UUID eventId, ResaleListingStatus status, Pageable pageable);

    /**
     * Row lock for the purchase flow (defends against a double-sale race the
     * same way {@code CartRepository.findByIdForUpdate} defends checkout -
     * see {@code ResaleListingServiceImpl.purchase}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ResaleListing l where l.id = :id")
    Optional<ResaleListing> findByIdForUpdate(@Param("id") UUID id);
}
