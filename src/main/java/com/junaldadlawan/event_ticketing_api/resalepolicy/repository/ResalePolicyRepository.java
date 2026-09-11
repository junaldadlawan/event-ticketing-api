package com.junaldadlawan.event_ticketing_api.resalepolicy.repository;

import com.junaldadlawan.event_ticketing_api.resalepolicy.entity.ResalePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResalePolicyRepository extends JpaRepository<ResalePolicy, UUID> {
    Optional<ResalePolicy> findByEventId(UUID eventId);
}
