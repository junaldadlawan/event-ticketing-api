package com.junaldadlawan.event_ticketing_api.layout.repository;

import com.junaldadlawan.event_ticketing_api.layout.entity.SeatLayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatLayoutRepository extends JpaRepository<SeatLayout, UUID> {

    List<SeatLayout> findByOrganizationIdAndDeletedAtIsNullOrderByNameAsc(UUID organizationId);

    Optional<SeatLayout> findByIdAndDeletedAtIsNull(UUID id);

    /** The row locked until the transaction ends, so two replaces never both get the same new version. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SeatLayout> findForUpdateByIdAndDeletedAtIsNull(UUID id);

    Optional<SeatLayout> findByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(UUID organizationId, String name);
}
