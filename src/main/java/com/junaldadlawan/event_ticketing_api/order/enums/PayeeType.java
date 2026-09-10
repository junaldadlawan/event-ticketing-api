package com.junaldadlawan.event_ticketing_api.order.enums;

/**
 * Who receives an Order's payment. This dispatch (Phase 5b checkout) only
 * ever sets {@code ORGANIZATION}. {@code USER} exists so Phase 7's
 * resale-purchase flow can reuse this same Order/Payment machinery later
 * (per the roadmap) - nothing in this dispatch builds or exercises that
 * path.
 */
public enum PayeeType {
    ORGANIZATION,
    USER
}
