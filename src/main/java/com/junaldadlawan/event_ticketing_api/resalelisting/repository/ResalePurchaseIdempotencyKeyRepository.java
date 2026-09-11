package com.junaldadlawan.event_ticketing_api.resalelisting.repository;

import com.junaldadlawan.event_ticketing_api.resalelisting.entity.ResalePurchaseIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResalePurchaseIdempotencyKeyRepository extends JpaRepository<ResalePurchaseIdempotencyKey, UUID> {
}
