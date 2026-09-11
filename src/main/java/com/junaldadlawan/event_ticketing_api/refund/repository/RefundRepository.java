package com.junaldadlawan.event_ticketing_api.refund.repository;

import com.junaldadlawan.event_ticketing_api.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByOrderId(UUID orderId);

    /**
     * Sum of already-{@code COMPLETED} refunds against an order (minor
     * units), used to compute the remaining refundable balance before
     * issuing another one - never lets cumulative refunds exceed the
     * order's total. {@code coalesce} so an order with zero refunds yet
     * sums to 0, not null.
     */
    @Query("select coalesce(sum(r.amount.amount), 0) from Refund r where r.orderId = :orderId and r.status = com.junaldadlawan.event_ticketing_api.refund.enums.RefundStatus.COMPLETED")
    long sumCompletedAmountByOrderId(@Param("orderId") UUID orderId);
}
