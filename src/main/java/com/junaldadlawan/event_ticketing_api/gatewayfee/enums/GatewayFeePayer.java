package com.junaldadlawan.event_ticketing_api.gatewayfee.enums;

/**
 * Who bears a fee on a purchase. A setting (the gateway fee, a platform fee rule) can only name CUSTOMER or ORGANIZATION. PLATFORM is kept only
 * because orders placed when it was allowed carry it in their snapshot: the platform absorbed the fee and the organizer was unaffected.
 */
public enum GatewayFeePayer {
    /** Added on top of the price and shown to the buyer in the cart. The platform keeps it to cover the gateway's cost. */
    CUSTOMER,
    /** Not shown to the buyer; deducted from the organizer's payout. */
    ORGANIZATION,
    /** No longer accepted in a setting; only on old orders. Absorbed by the platform: the buyer and the organizer are unaffected. */
    PLATFORM
}
