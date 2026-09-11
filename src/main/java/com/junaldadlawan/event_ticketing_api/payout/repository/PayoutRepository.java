package com.junaldadlawan.event_ticketing_api.payout.repository;

import com.junaldadlawan.event_ticketing_api.payout.entity.Payout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Page<Payout> findByOrganizationId(UUID organizationId, Pageable pageable);
}
