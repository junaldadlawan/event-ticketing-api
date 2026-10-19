package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Page<Payout> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** Locks the row so two sends (or a send and a webhook) of one payout never run at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

    /**
     * SCHEDULED -> CANCELLED, only while it is still SCHEDULED: a send claims the row first and moves it to PROCESSING, so a cancel that
     * loses that race changes nothing (0). Returns how many rows changed.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Payout p set p.status = com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus.CANCELLED, p.updatedAt = :now"
            + " where p.id = :id and p.status = com.junaldadlawan.event_ticketing_api.payout.enums.PayoutStatus.SCHEDULED")
    int cancelIfScheduled(@Param("id") UUID id, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.transferId = :transferId")
    Optional<Payout> findByTransferIdForUpdate(@Param("transferId") String transferId);
}
