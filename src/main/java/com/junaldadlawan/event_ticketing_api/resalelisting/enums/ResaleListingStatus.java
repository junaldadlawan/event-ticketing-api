package com.junaldadlawan.event_ticketing_api.resalelisting.enums;

public enum ResaleListingStatus {
    ACTIVE,
    SOLD,
    CANCELLED,
    /** Defined for schema parity with openapi.yaml - no expiry job exists yet to ever set this. */
    EXPIRED
}
