package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.CheckoutIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckoutIdempotencyKeyRepository extends JpaRepository<CheckoutIdempotencyKey, UUID> {
}
