package com.junaldadlawan.event_ticketing_api.event.enums;

public enum EventStatus {
    DRAFT,
    PUBLISHED,
    ON_SALE,
    SOLD_OUT,
    CANCELLED,
    COMPLETED,
    /** Phase 12 (BR-ADMIN-002) - admin moderation action, see {@code ModerationActionServiceImpl}. */
    SUSPENDED
}
