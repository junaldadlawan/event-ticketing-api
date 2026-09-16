package com.junaldadlawan.event_ticketing_api.organization.enums;

public enum OrganizationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Phase 12 (BR-ADMIN-002) - admin moderation action, see {@code ModerationActionServiceImpl}. */
    SUSPENDED
}
