package com.junaldadlawan.event_ticketing_api.platformfee.enums;

/** What a platform fee rule applies to. The most specific scope wins: EVENT, then ORGANIZATION, then PLATFORM. */
public enum FeeScope {
    /** Every order that has no more specific rule. */
    PLATFORM,
    /** Every event of one organization that has no event rule. */
    ORGANIZATION,
    /** One event. */
    EVENT
}
