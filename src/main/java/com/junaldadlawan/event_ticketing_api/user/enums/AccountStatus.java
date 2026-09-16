package com.junaldadlawan.event_ticketing_api.user.enums;

/** Phase 12 (BR-ADMIN-002) - real, enforced account state, not just a moderation-log entry. See {@code AuthServiceImpl.login}. */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED
}
