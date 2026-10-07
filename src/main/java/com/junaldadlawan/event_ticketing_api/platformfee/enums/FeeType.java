package com.junaldadlawan.event_ticketing_api.platformfee.enums;

/** How a platform fee is worked out for one order. */
public enum FeeType {
    /** A percentage of the ticket total (after promo discounts), rounded half up to the minor unit. */
    PERCENTAGE,
    /** A fixed amount per order. */
    FLAT
}
