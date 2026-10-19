package com.junaldadlawan.event_ticketing_api.payout.enums;

public enum PayoutStatus {
    SCHEDULED,
    /** The transfer is with the payment provider and has not settled yet. */
    PROCESSING,
    PAID,
    FAILED,
    /** Cancelled by an admin while still SCHEDULED; its orders were released and are owed again. Final. */
    CANCELLED
}
