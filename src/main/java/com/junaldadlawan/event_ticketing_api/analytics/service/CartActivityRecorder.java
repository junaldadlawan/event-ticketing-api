package com.junaldadlawan.event_ticketing_api.analytics.service;

import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository;
import com.junaldadlawan.event_ticketing_api.analytics.repository.FunnelRepository.CartAction;
import com.junaldadlawan.event_ticketing_api.cart.entity.CartItem;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Savepoint;
import java.time.Instant;

/**
 * Writes the funnel's cart log (BR-ANALYTICS-003) from inside the cart change's own transaction. The row is written under a JDBC savepoint
 * on that transaction's connection, so a failed write is rolled back alone and logged: it never fails or undoes the cart change it records
 * (Postgres would otherwise refuse every later statement of the transaction).
 */
@Slf4j
@Component
public class CartActivityRecorder {

    private final FunnelRepository funnelRepository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    /** {@code enabled} is false only for the test run, which shares the dev database with every cart test (see the pom). */
    public CartActivityRecorder(FunnelRepository funnelRepository, JdbcTemplate jdbcTemplate,
                                @Value("${app.analytics.cart-activity.enabled:true}") boolean enabled) {
        this.funnelRepository = funnelRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    /** {@code quantity} is the tickets the change concerns (the increase, the decrease, the line's quantity); nothing is written for 0. */
    public void record(CartItem item, int quantity, CartAction action) {
        if (!enabled || quantity <= 0) {
            return;
        }
        Runnable insert = () -> funnelRepository.insertCartActivity(item.getCartId(), item.getId(), item.getTicketTypeId(), quantity, action, Instant.now());
        try {
            // the same connection the surrounding transaction uses (JdbcTemplate joins it)
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                if (connection.getAutoCommit()) {
                    insert.run();   // no surrounding transaction: nothing else to protect
                    return null;
                }
                Savepoint savepoint = connection.setSavepoint();
                try {
                    insert.run();
                    connection.releaseSavepoint(savepoint);
                } catch (RuntimeException e) {
                    connection.rollback(savepoint);
                    throw e;
                }
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("Could not record cart activity {} for cart item {}: {}", action, item.getId(), e.toString());
        }
    }
}
