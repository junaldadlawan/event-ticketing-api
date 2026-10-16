package com.junaldadlawan.event_ticketing_api.platformfee.enums;

/** How a platform fee is worked out for one order: the percentage on its ticket total, the fixed amount for each of its tickets. */
public enum FeeType {
    /** A percentage of the ticket total (after promo discounts), rounded half up to the minor unit. */
    PERCENTAGE,
    /** A fixed amount per ticket. */
    FLAT,
    /** Both at once: a percentage of the ticket total plus a fixed amount per ticket. */
    COMBINED
}
