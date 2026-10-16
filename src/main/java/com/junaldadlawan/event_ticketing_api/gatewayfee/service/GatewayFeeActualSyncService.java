package com.junaldadlawan.event_ticketing_api.gatewayfee.service;

import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler;
import com.junaldadlawan.event_ticketing_api.gatewayfee.gateway.PaymentFeeSampler.PaymentSample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fills in what the payment gateway really kept on orders ({@code orders.gateway_fee_actual_amount}), taken from the gateway's own payment
 * records, so the finance figures show the real fee even for orders placed before fees were recorded on the order. Only PayMongo orders
 * (payment id {@code pay_...}) are touched, only those still missing it, and the gateway is only asked when there are some. It reads the latest
 * {@link #WINDOW} paid payments, so an older order than that stays unknown until it is within reach. Best-effort and throttled: a gateway that is
 * down or slow never breaks the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GatewayFeeActualSyncService {

    static final int WINDOW = 100;
    private static final Duration THROTTLE = Duration.ofSeconds(60);

    private final JdbcTemplate jdbc;
    private final PaymentFeeSampler sampler;

    private volatile Instant lastAttempt = Instant.EPOCH;

    /** The orders updated. Runs in its own transaction, so it can be called from a read-only one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int fillMissing() {
        if (!"PAYMONGO".equals(sampler.provider()) || Instant.now().isBefore(lastAttempt.plus(THROTTLE))) {
            return 0;
        }
        List<Map<String, Object>> missing = jdbc.queryForList("""
                select o.id, p.gateway_ref from orders o join payments p on p.order_id = o.id
                where o.gateway_fee_actual_amount is null and p.gateway_ref like 'pay\\_%'""");
        if (missing.isEmpty()) {
            return 0;
        }
        lastAttempt = Instant.now();
        Map<String, Long> actualByPayment = new HashMap<>();
        for (PaymentSample sample : sampler.recentPaidPayments(WINDOW)) {
            // what the gateway kept: the amount minus the net it paid out, or the two fees when it gives no net
            actualByPayment.put(sample.paymentId(), sample.netAmount() > 0 ? sample.amount() - sample.netAmount() : sample.fee() + sample.foreignFee());
        }
        int updated = 0;
        for (Map<String, Object> row : missing) {
            Long actual = actualByPayment.get((String) row.get("gateway_ref"));
            if (actual != null) {
                updated += jdbc.update("update orders set gateway_fee_actual_amount = ? where id = ? and gateway_fee_actual_amount is null",
                        actual, (UUID) row.get("id"));
            }
        }
        log.info("Gateway fee: filled the real fee on {} of {} orders that lacked it", updated, missing.size());
        return updated;
    }
}
