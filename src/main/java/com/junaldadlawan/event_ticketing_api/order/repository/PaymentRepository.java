package com.junaldadlawan.event_ticketing_api.order.repository;

import com.junaldadlawan.event_ticketing_api.order.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderId(UUID orderId);
}
