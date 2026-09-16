package com.junaldadlawan.event_ticketing_api.dispute.enums;

/** BR-ADMIN-003. {@code RESOLVED}/{@code DISMISSED} are terminal - see {@code DisputeServiceImpl.update}. */
public enum DisputeStatus {
    OPEN,
    INVESTIGATING,
    RESOLVED,
    DISMISSED
}
