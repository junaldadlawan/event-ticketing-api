package com.junaldadlawan.event_ticketing_api.order.enums;

/**
 * Per BR-CART-002 ("an order is created only on successful payment"), this
 * dispatch (Phase 5b checkout) only ever creates an Order directly as
 * {@code PAID} - no Order is ever persisted in {@code PENDING} waiting for
 * payment. {@code CANCELLED}/{@code REFUNDED}/{@code PARTIALLY_REFUNDED}
 * exist for later phases (e.g. refunds in Phase 8).
 */
public enum OrderStatus {
    PENDING,
    PAID,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}
