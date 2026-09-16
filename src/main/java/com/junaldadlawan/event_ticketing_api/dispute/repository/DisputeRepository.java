package com.junaldadlawan.event_ticketing_api.dispute.repository;

import com.junaldadlawan.event_ticketing_api.dispute.entity.Dispute;
import com.junaldadlawan.event_ticketing_api.dispute.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    /** {@code GET /disputes?status=} - admin-only listing, optional status filter (see {@code DisputeServiceImpl.list}). */
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);
}
