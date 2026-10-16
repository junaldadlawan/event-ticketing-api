package com.junaldadlawan.event_ticketing_api.payout.enums;

/** Which events the batch payout screen lists funds for. */
public enum EventScope {
    /** Only events that are over: status COMPLETED, or ended and not cancelled, suspended or a draft. */
    COMPLETED,
    /** Every event, over or not. */
    ALL
}
