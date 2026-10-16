package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Page<Payout> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** Locks the row so two sends (or a send and a webhook) of one payout never run at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.transferId = :transferId")
    Optional<Payout> findByTransferIdForUpdate(@Param("transferId") String transferId);
}
