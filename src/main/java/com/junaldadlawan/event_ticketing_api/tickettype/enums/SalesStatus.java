package com.junaldadlawan.event_ticketing_api.tickettype.enums;

/**
 * Whether a ticket type is currently being sold: {@code ACTIVE} (the normal state - the sale window and the
 * event's status still decide whether it can actually be bought) or {@code PAUSED} (the organizer stopped
 * selling it: it can't be added to a cart, and a cart that already holds it can't check out).
 */
public enum SalesStatus {
    ACTIVE,
    PAUSED
}
