package com.junaldadlawan.event_ticketing_api.refundpolicy.repository;

import com.junaldadlawan.event_ticketing_api.refundpolicy.entity.RefundPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, UUID> {
    Optional<RefundPolicy> findByEventId(UUID eventId);
}
